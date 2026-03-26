package com.zuqi.service.impl;

import com.zuqi.api.dto.approval.CreateApprovalRequestDto;
import com.zuqi.api.dto.order.*;
import com.zuqi.domain.approval.ApprovalWorkflowType;
import com.zuqi.domain.distributor.Distributor;
import com.zuqi.domain.inventory.Stock;
import com.zuqi.domain.inventory.Warehouse;
import com.zuqi.domain.customer.Customer;
import com.zuqi.domain.order.*;
import com.zuqi.domain.product.Product;
import com.zuqi.domain.user.User;
import com.zuqi.domain.audit.ActivityAction;
import com.zuqi.exception.ResourceNotFoundException;
import com.zuqi.exception.ValidationException;
import com.zuqi.repository.*;
import com.zuqi.ai.event.DeliveryCompletedEvent;
import com.zuqi.service.ActivityLogService;
import com.zuqi.ai.event.OrderCreatedEvent;
import com.zuqi.ai.feature.FeatureStore;
import com.zuqi.domain.credit.CreditLimit;
import com.zuqi.domain.credit.CreditLimitStatus;
import com.zuqi.repository.CreditLimitRepository;
import com.zuqi.service.ApprovalService;
import com.zuqi.service.ApprovalThresholdService;
import com.zuqi.service.InvoiceService;
import com.zuqi.service.OrderService;
import com.zuqi.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusHistoryRepository statusHistoryRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final DistributorRepository distributorRepository;
    private final WarehouseRepository warehouseRepository;
    private final StockRepository stockRepository;
    private final UserRepository userRepository;
    private final CreditLimitRepository creditLimitRepository;
    private final InvoiceRepository invoiceRepository;
    private final SecurityUtils securityUtils;
    private final ActivityLogService activityLogService;
    private final InvoiceService invoiceService;
    private final ApplicationEventPublisher eventPublisher;
    private final FeatureStore featureStore;
    private final ApprovalThresholdService approvalThresholdService;

    @Lazy @Autowired
    private ApprovalService approvalService;

    @Override
    public Page<OrderResponse> getAllOrders(Pageable pageable) {
        UUID merchantId = securityUtils.getCurrentUserMerchantId();
        if (merchantId != null) {
            return orderRepository.findByDistributorMerchantId(merchantId, pageable)
                    .map(OrderResponse::fromEntity);
        }
        UUID distributorId = securityUtils.getDistributorIdForFiltering();
        if (distributorId != null) {
            return orderRepository.findByDistributorId(distributorId, pageable)
                    .map(OrderResponse::fromEntity);
        }
        return orderRepository.findAll(pageable).map(OrderResponse::fromEntity);
    }

    @Override
    public Page<OrderResponse> getOrdersByDistributor(UUID distributorId, Pageable pageable) {
        return orderRepository.findByDistributorId(distributorId, pageable)
                .map(OrderResponse::fromEntity);
    }

    @Override
    public Page<OrderResponse> getOrdersByMerchant(UUID merchantId, Pageable pageable) {
        return orderRepository.findByMerchantId(merchantId, pageable)
                .map(OrderResponse::fromEntity);
    }

    @Override
    public Page<OrderResponse> getOrdersBySalesRep(UUID salesRepId, Pageable pageable) {
        return orderRepository.findBySalesRepId(salesRepId, pageable)
                .map(OrderResponse::fromEntity);
    }

    @Override
    public Page<OrderResponse> getOrdersByFilters(
            UUID distributorId,
            OrderStatus status,
            UUID merchantId,
            UUID salesRepId,
            PaymentStatus paymentStatus,
            LocalDate startDate,
            LocalDate endDate,
            Pageable pageable) {

        LocalDateTime startDateTime = startDate != null ? startDate.atStartOfDay() : null;
        LocalDateTime endDateTime = endDate != null ? endDate.atTime(23, 59, 59) : null;

        // Determine effective distributor ID for filtering
        UUID effectiveDistributorId = distributorId;
        if (effectiveDistributorId == null) {
            effectiveDistributorId = securityUtils.getDistributorIdForFiltering();
        }

        // Use unsorted pageable since the native query has its own ORDER BY clause
        Pageable unsortedPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());

        return orderRepository.findByFilters(
                effectiveDistributorId,
                status != null ? status.name() : null,
                merchantId,
                salesRepId,
                paymentStatus != null ? paymentStatus.name() : null,
                startDateTime,
                endDateTime,
                unsortedPageable
        ).map(OrderResponse::fromEntity);
    }

    @Override
    public Page<OrderResponse> searchOrders(UUID distributorId, String search, Pageable pageable) {
        UUID effectiveDistributorId = distributorId;
        if (effectiveDistributorId == null) {
            UUID merchantId = securityUtils.getCurrentUserMerchantId();
            if (merchantId != null) {
                return orderRepository.searchOrdersByMerchant(merchantId, search, pageable)
                        .map(OrderResponse::fromEntity);
            }
            effectiveDistributorId = securityUtils.getDistributorIdForFiltering();
        }

        return orderRepository.searchOrders(effectiveDistributorId, search, pageable)
                .map(OrderResponse::fromEntity);
    }

    @Override
    public OrderResponse getOrderById(UUID id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", id));
        return OrderResponse.fromEntity(order);
    }

    @Override
    public OrderResponse getOrderByNumber(String orderNumber) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "orderNumber", orderNumber));
        return OrderResponse.fromEntity(order);
    }

    @Override
    @Transactional
    public OrderResponse createOrder(OrderRequest request, User currentUser) {
        log.info("Creating order for merchant: {}", request.getMerchantId());

        // Validate and fetch related entities
        Distributor distributor = distributorRepository.findById(request.getDistributorId())
                .orElseThrow(() -> new ResourceNotFoundException("Distributor", "id", request.getDistributorId()));

        Customer merchant = customerRepository.findById(request.getMerchantId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", request.getMerchantId()));

        User salesRep = null;
        if (request.getSalesRepId() != null) {
            salesRep = userRepository.findById(request.getSalesRepId())
                    .orElseThrow(() -> new ResourceNotFoundException("User", "id", request.getSalesRepId()));
        }

        Warehouse warehouse = null;
        if (request.getWarehouseId() != null) {
            warehouse = warehouseRepository.findById(request.getWarehouseId())
                    .orElseThrow(() -> new ResourceNotFoundException("Warehouse", "id", request.getWarehouseId()));
        }

        // Credit gating for CREDIT orders — block if order exceeds available credit
        OrderType orderType = request.getOrderType() != null ? request.getOrderType() : OrderType.STANDARD;
        if (orderType == OrderType.CREDIT) {
            validateCreditLimit(merchant, request);
        }

        // Generate order number
        String orderNumber = generateOrderNumber();

        // Create order
        Order order = Order.builder()
                .orderNumber(orderNumber)
                .distributor(distributor)
                .merchant(merchant)
                .salesRep(salesRep != null ? salesRep : currentUser)
                .warehouse(warehouse)
                .orderType(request.getOrderType() != null ? request.getOrderType() : OrderType.STANDARD)
                .status(OrderStatus.PENDING)
                .paymentStatus(PaymentStatus.PENDING)
                .discountAmount(request.getDiscountAmount() != null ? request.getDiscountAmount() : BigDecimal.ZERO)
                .paymentTermsDays(request.getPaymentTermsDays() != null ? request.getPaymentTermsDays() : merchant.getPaymentTermsDays())
                .deliveryAddress(request.getDeliveryAddress() != null ? request.getDeliveryAddress() : merchant.getAddress())
                .deliveryLatitude(request.getDeliveryLatitude())
                .deliveryLongitude(request.getDeliveryLongitude())
                .notes(request.getNotes())
                .subtotal(BigDecimal.ZERO)
                .totalAmount(BigDecimal.ZERO)
                .build();

        // Add order items
        for (OrderItemRequest itemRequest : request.getItems()) {
            Product product = productRepository.findById(itemRequest.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product", "id", itemRequest.getProductId()));

            BigDecimal discountPct = itemRequest.getDiscountPercent() != null ? itemRequest.getDiscountPercent() : BigDecimal.ZERO;
            BigDecimal effectivePrice = product.getUnitPrice().subtract(
                    product.getUnitPrice().multiply(discountPct).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP));
            if (product.getMinSalePrice() != null && effectivePrice.compareTo(product.getMinSalePrice()) < 0) {
                throw new ValidationException("Price for " + product.getName() + " is below the minimum allowed price of " + product.getMinSalePrice());
            }

            OrderItem item = OrderItem.builder()
                    .product(product)
                    .quantity(itemRequest.getQuantity())
                    .unitPrice(product.getUnitPrice())
                    .discountPercent(discountPct)
                    .build();

            item.calculateTotal();
            order.addItem(item);
        }

        // Calculate order totals
        order.calculateTotals();

        // Set payment due date
        if (order.getPaymentTermsDays() != null && order.getPaymentTermsDays() > 0) {
            order.setPaymentDueDate(LocalDate.now().plusDays(order.getPaymentTermsDays()));
        }

        // Credit limit block: check Customer.creditLimit (built-in field, all order types)
        if (merchant.getCreditLimit() != null && merchant.getCreditLimit().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal unpaidBalance = invoiceRepository.sumUnpaidByCustomerId(merchant.getId());
            if (unpaidBalance == null) unpaidBalance = BigDecimal.ZERO;
            if (unpaidBalance.add(order.getTotalAmount()).compareTo(merchant.getCreditLimit()) > 0) {
                throw new ValidationException(
                    "Order exceeds credit limit for " + merchant.getBusinessName() +
                    ". Limit: " + merchant.getCreditLimit() +
                    ", Current outstanding: " + unpaidBalance +
                    ", New order total: " + order.getTotalAmount());
            }
        }

        // Approval routing for INITIATOR users
        boolean needsApproval = securityUtils.currentUserHasWorkflowTier("INITIATOR");
        if (needsApproval) {
            order.setApprovalStatus("PENDING_APPROVAL");
            order.setStatus(OrderStatus.PENDING);
        }

        // Save order
        order = orderRepository.save(order);

        // Sync customer's stored currentBalance with real outstanding from all unpaid orders
        try {
            BigDecimal outstanding = orderRepository.sumOutstandingByCustomerId(merchant.getId());
            merchant.setCurrentBalance(outstanding != null ? outstanding : BigDecimal.ZERO);
            customerRepository.save(merchant);
        } catch (Exception e) {
            log.warn("Failed to sync currentBalance for customer {}: {}", merchant.getId(), e.getMessage());
        }

        // Add initial status history
        addStatusHistory(order, OrderStatus.PENDING, "Order created", currentUser);

        // Deduct stock immediately at order creation
        if (order.getWarehouse() != null) {
            deductStockForOrder(order);
        }

        if (needsApproval) {
            // Route through maker-checker — defer invoice creation to approval callback
            int requiredApprovals = approvalThresholdService.getRequiredApprovals(
                    distributor.getId(), ApprovalWorkflowType.SALES_ORDER, order.getTotalAmount());
            try {
                approvalService.createRequest(currentUser.getId(), CreateApprovalRequestDto.builder()
                        .workflowType(ApprovalWorkflowType.SALES_ORDER)
                        .entityType("ORDER")
                        .entityId(order.getId())
                        .entityName(order.getOrderNumber())
                        .description("Sales Order " + order.getOrderNumber() + " — KES " + order.getTotalAmount())
                        .requiredApprovals(requiredApprovals)
                        .amount(order.getTotalAmount())
                        .build());
            } catch (Exception e) {
                log.error("Failed to create approval request for order {}: {}", order.getOrderNumber(), e.getMessage());
            }
        } else {
            // Standard flow: create invoice immediately
            try {
                invoiceService.createInvoiceFromOrder(order);
                log.info("Invoice created for order: {}", order.getOrderNumber());
            } catch (Exception e) {
                log.error("Failed to create invoice for order {}: {}", order.getOrderNumber(), e.getMessage());
                // Don't fail the order creation if invoice creation fails
            }
        }

        // Invalidate demand feature cache for this merchant (order affects demand forecasting)
        featureStore.invalidateMerchantCache(order.getMerchant().getId());
        log.debug("Invalidated feature cache for merchant {} after order creation", order.getMerchant().getId());

        // Publish AI event for data quality validation and demand forecasting
        publishOrderCreatedEvent(order);

        activityLogService.log(currentUser.getId(), currentUser.getEmail(),
                currentUser.getFirstName() + " " + currentUser.getLastName(),
                ActivityAction.CREATE, "ORDER", order.getId(),
                order.getOrderNumber(), "ORDERS", "Created order: " + order.getOrderNumber());

        log.info("Order created successfully: {}", order.getOrderNumber());
        return OrderResponse.fromEntity(order);
    }

    @Override
    @Transactional
    public OrderResponse updateOrder(UUID id, OrderRequest request) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", id));

        // Only allow updates for pending orders
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new ValidationException("Cannot update order with status: " + order.getStatus());
        }

        // Update basic fields
        if (request.getNotes() != null) {
            order.setNotes(request.getNotes());
        }
        if (request.getDiscountAmount() != null) {
            order.setDiscountAmount(request.getDiscountAmount());
        }
        if (request.getDeliveryAddress() != null) {
            order.setDeliveryAddress(request.getDeliveryAddress());
        }

        // Update items if provided
        if (request.getItems() != null && !request.getItems().isEmpty()) {
            // Clear existing items
            order.getItems().clear();

            // Add new items
            for (OrderItemRequest itemRequest : request.getItems()) {
                Product product = productRepository.findById(itemRequest.getProductId())
                        .orElseThrow(() -> new ResourceNotFoundException("Product", "id", itemRequest.getProductId()));

                OrderItem item = OrderItem.builder()
                        .product(product)
                        .quantity(itemRequest.getQuantity())
                        .unitPrice(product.getUnitPrice())
                        .discountPercent(itemRequest.getDiscountPercent() != null ? itemRequest.getDiscountPercent() : BigDecimal.ZERO)
                        .build();

                item.calculateTotal();
                order.addItem(item);
            }

            // Recalculate totals
            order.calculateTotals();
        }

        order = orderRepository.save(order);
        return OrderResponse.fromEntity(order);
    }

    @Override
    @Transactional
    public OrderResponse updateOrderStatus(UUID id, StatusUpdateRequest request, User currentUser) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", id));

        OrderStatus currentStatus = order.getStatus();
        OrderStatus newStatus = request.getStatus();

        // Validate status transition
        validateStatusTransition(currentStatus, newStatus);

        order.setStatus(newStatus);

        // Add status history
        addStatusHistory(order, newStatus, request.getNotes(), currentUser);

        order = orderRepository.save(order);
        log.info("Order {} status updated from {} to {}", order.getOrderNumber(), currentStatus, newStatus);

        // Publish delivery event when order is delivered — closes the route optimization feedback loop
        if (newStatus == OrderStatus.DELIVERED) {
            publishDeliveryCompletedEvent(order, true, request.getNotes());
        }

        return OrderResponse.fromEntity(order);
    }

    @Override
    @Transactional
    public OrderResponse cancelOrder(UUID id, String reason, User currentUser) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", id));

        // Can only cancel pending, confirmed, or processing orders
        if (order.getStatus() == OrderStatus.DELIVERED || order.getStatus() == OrderStatus.CANCELLED) {
            throw new ValidationException("Cannot cancel order with status: " + order.getStatus());
        }

        order.setStatus(OrderStatus.CANCELLED);
        addStatusHistory(order, OrderStatus.CANCELLED, reason, currentUser);

        // Restore stock when order is cancelled
        if (order.getWarehouse() != null && order.getItems() != null) {
            restoreStockForOrder(order);
        }

        order = orderRepository.save(order);
        log.info("Order {} cancelled", order.getOrderNumber());

        return OrderResponse.fromEntity(order);
    }

    @Override
    public List<OrderStatusHistoryResponse> getOrderStatusHistory(UUID orderId) {
        // Verify order exists
        if (!orderRepository.existsById(orderId)) {
            throw new ResourceNotFoundException("Order", "id", orderId);
        }

        return statusHistoryRepository.findByOrderIdOrderByCreatedAtDesc(orderId)
                .stream()
                .map(OrderStatusHistoryResponse::fromEntity)
                .toList();
    }

    @Override
    public long getOrderCountByStatus(UUID distributorId, OrderStatus status) {
        return orderRepository.countByDistributorIdAndStatus(distributorId, status);
    }

    @Override
    public List<OrderResponse> getOverdueOrders() {
        return orderRepository.findOverdueOrders(LocalDate.now())
                .stream()
                .map(OrderResponse::fromEntity)
                .toList();
    }

    // Helper methods

    private void deductStockForOrder(Order order) {
        for (OrderItem item : order.getItems()) {
            Stock stock = stockRepository
                    .findByWarehouseIdAndProductId(order.getWarehouse().getId(), item.getProduct().getId())
                    .orElse(null);
            if (stock == null) {
                stock = Stock.builder()
                        .warehouse(order.getWarehouse())
                        .product(item.getProduct())
                        .quantity(BigDecimal.ZERO)
                        .reservedQuantity(BigDecimal.ZERO)
                        .build();
            }
            stock.setQuantity(stock.getQuantity().subtract(item.getQuantity()));
            stockRepository.save(stock);
            log.info("Deducted {} of '{}' for order {}", item.getQuantity(), item.getProduct().getName(), order.getOrderNumber());
        }
    }

    private void restoreStockForOrder(Order order) {
        for (OrderItem item : order.getItems()) {
            Stock stock = stockRepository
                    .findByWarehouseIdAndProductId(order.getWarehouse().getId(), item.getProduct().getId())
                    .orElse(null);
            if (stock == null) return;
            stock.setQuantity(stock.getQuantity().add(item.getQuantity()));
            stockRepository.save(stock);
            log.info("Restored {} of '{}' on cancellation of order {}", item.getQuantity(), item.getProduct().getName(), order.getOrderNumber());
        }
    }

    private String generateOrderNumber() {
        String prefix = "ORD-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-";
        Integer maxNum = orderRepository.findMaxOrderNumberByPrefix(prefix);
        int nextNum = (maxNum != null ? maxNum : 0) + 1;
        return prefix + String.format("%04d", nextNum);
    }

    private void addStatusHistory(Order order, OrderStatus status, String notes, User changedBy) {
        OrderStatusHistory history = OrderStatusHistory.builder()
                .order(order)
                .status(status)
                .notes(notes)
                .changedBy(changedBy)
                .build();
        statusHistoryRepository.save(history);
    }

    private void validateStatusTransition(OrderStatus from, OrderStatus to) {
        // Define valid transitions
        boolean valid = switch (from) {
            case PENDING -> to == OrderStatus.CONFIRMED || to == OrderStatus.CANCELLED;
            case CONFIRMED -> to == OrderStatus.PROCESSING || to == OrderStatus.CANCELLED;
            case PROCESSING -> to == OrderStatus.READY_FOR_DELIVERY || to == OrderStatus.CANCELLED;
            case READY_FOR_DELIVERY -> to == OrderStatus.OUT_FOR_DELIVERY || to == OrderStatus.CANCELLED;
            case OUT_FOR_DELIVERY -> to == OrderStatus.DELIVERED || to == OrderStatus.CANCELLED;
            case DELIVERED, CANCELLED -> false;
        };

        if (!valid) {
            throw new ValidationException("Invalid status transition from " + from + " to " + to);
        }
    }

    private void validateCreditLimit(Customer merchant, OrderRequest request) {
        UUID distributorId = request.getDistributorId();
        creditLimitRepository.findByMerchantIdAndDistributorIdAndStatus(
                merchant.getId(), distributorId, CreditLimitStatus.ACTIVE
        ).ifPresent(creditLimit -> {
            // Estimate order total from item requests
            BigDecimal estimatedTotal = BigDecimal.ZERO;
            for (OrderItemRequest item : request.getItems()) {
                Product product = productRepository.findById(item.getProductId()).orElse(null);
                if (product != null) {
                    BigDecimal lineTotal = product.getUnitPrice().multiply(item.getQuantity());
                    estimatedTotal = estimatedTotal.add(lineTotal);
                }
            }

            BigDecimal available = creditLimit.getAvailableLimit();
            if (available != null && estimatedTotal.compareTo(available) > 0) {
                throw new ValidationException(String.format(
                        "Order value KES %,.2f exceeds available credit limit KES %,.2f for merchant %s",
                        estimatedTotal, available, merchant.getBusinessName()));
            }
        });
        // If no active credit limit exists, allow the order (merchant may not have credit facility set up)
    }

    private void publishDeliveryCompletedEvent(Order order, boolean successful, String notes) {
        try {
            DeliveryCompletedEvent event = new DeliveryCompletedEvent(
                    order.getId(),       // deliveryId (using orderId as reference)
                    order.getId(),       // orderId
                    order.getMerchant().getId(),
                    order.getSalesRep() != null ? order.getSalesRep().getId() : null,
                    order.getDistributor().getId(),
                    null,                // routeId — handler will skip if null
                    null,                // scheduledTime
                    LocalDateTime.now(), // actualTime
                    null,                // delayMinutes
                    successful,
                    notes
            );
            eventPublisher.publishEvent(event);
            log.debug("Published DeliveryCompletedEvent for order {}", order.getOrderNumber());
        } catch (Exception e) {
            log.warn("Failed to publish DeliveryCompletedEvent for order {}: {}",
                    order.getOrderNumber(), e.getMessage());
        }
    }

    private void publishOrderCreatedEvent(Order order) {
        List<OrderCreatedEvent.OrderItem> eventItems = order.getItems().stream()
                .map(item -> new OrderCreatedEvent.OrderItem(
                        item.getProduct().getId(),
                        item.getQuantity().intValue(),
                        item.getUnitPrice(),
                        item.getTotalAmount()
                ))
                .toList();

        OrderCreatedEvent event = new OrderCreatedEvent(
                order.getId(),
                order.getMerchant().getId(),
                order.getSalesRep() != null ? order.getSalesRep().getId() : null,
                order.getDistributor().getId(),
                order.getTotalAmount(),
                eventItems,
                order.getCreatedAt(),
                order.getOrderType().name()
        );
        eventPublisher.publishEvent(event);
        log.debug("Published OrderCreatedEvent for order {}", order.getId());
    }
}
