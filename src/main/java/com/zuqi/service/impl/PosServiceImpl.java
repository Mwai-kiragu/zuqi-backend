package com.zuqi.service.impl;

import com.zuqi.api.dto.pos.*;
import com.zuqi.domain.branch.DistributorBranch;
import com.zuqi.domain.customer.Customer;
import com.zuqi.domain.inventory.*;
import com.zuqi.domain.order.Order;
import com.zuqi.domain.order.OrderItem;
import com.zuqi.domain.order.OrderStatus;
import com.zuqi.domain.order.OrderType;
import com.zuqi.domain.order.PaymentStatus;
import com.zuqi.domain.pos.*;
import com.zuqi.domain.product.Product;
import com.zuqi.domain.user.User;
import com.zuqi.exception.ResourceNotFoundException;
import com.zuqi.exception.ValidationException;
import com.zuqi.event.PosSaleCompletedEvent;
import com.zuqi.repository.*;
import com.zuqi.service.GlAutoPostingService;
import com.zuqi.service.InvoiceService;
import com.zuqi.service.PaymentService;
import com.zuqi.service.PosService;
import com.zuqi.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class PosServiceImpl implements PosService {

    private final PosTerminalRepository terminalRepository;
    private final PosShiftRepository shiftRepository;
    private final PosSaleRepository saleRepository;
    private final PosSaleItemRepository saleItemRepository;
    private final PosSalePaymentRepository paymentRepository;
    private final DistributorBranchRepository branchRepository;
    private final ProductRepository productRepository;
    private final StockRepository stockRepository;
    private final StockMovementRepository stockMovementRepository;
    private final WarehouseRepository warehouseRepository;
    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;
    private final SecurityUtils securityUtils;
    private final InvoiceService invoiceService;
    private final PaymentService paymentService;
    private final GlAutoPostingService glAutoPostingService;
    private final ApplicationEventPublisher eventPublisher;


    @Override
    @Transactional
    public PosTerminalResponse createTerminal(PosTerminalRequest request, UUID createdByUserId) {
        DistributorBranch branch = branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Branch", "id", request.getBranchId()));

        if (request.getCode() != null && terminalRepository.existsByCode(request.getCode())) {
            throw new ValidationException("Terminal code already exists");
        }

        User createdBy = userRepository.findById(createdByUserId).orElse(null);

        PosTerminal terminal = PosTerminal.builder()
                .branch(branch)
                .name(request.getName())
                .code(request.getCode())
                .status(PosTerminalStatus.ACTIVE)
                .createdBy(createdBy)
                .build();

        terminal = terminalRepository.save(terminal);
        return mapToTerminalResponse(terminal);
    }

    @Override
    public List<PosTerminalResponse> getTerminalsByBranch(UUID branchId) {
        UUID effectiveBranchId = securityUtils.getEffectiveBranchId();
        // Non-null effectiveBranchId means enforced branch scope (non-HQ user)
        UUID filterBranchId = effectiveBranchId != null ? effectiveBranchId : branchId;
        if (filterBranchId == null) {
            // HQ branch or SUPER_ADMIN: return all terminals
            return terminalRepository.findAll().stream()
                    .map(this::mapToTerminalResponse)
                    .collect(Collectors.toList());
        }
        return terminalRepository.findByBranchId(filterBranchId).stream()
                .map(this::mapToTerminalResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public PosShiftResponse openShift(OpenShiftRequest request, UUID cashierId) {
        DistributorBranch branch = branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Branch", "id", request.getBranchId()));

        // Check if cashier already has open shift
        shiftRepository.findByBranchIdAndCashierIdAndStatus(request.getBranchId(), cashierId, PosShiftStatus.OPEN)
                .ifPresent(s -> { throw new ValidationException("Cashier already has an open shift"); });

        User cashier = userRepository.findById(cashierId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", cashierId));

        PosTerminal terminal = null;
        if (request.getTerminalId() != null) {
            terminal = terminalRepository.findById(request.getTerminalId())
                    .orElseThrow(() -> new ResourceNotFoundException("Terminal", "id", request.getTerminalId()));
        }

        PosShift shift = PosShift.builder()
                .branch(branch)
                .terminal(terminal)
                .cashier(cashier)
                .status(PosShiftStatus.OPEN)
                .openingFloat(request.getOpeningFloat() != null ? request.getOpeningFloat() : BigDecimal.ZERO)
                .notes(request.getNotes())
                .openedAt(LocalDateTime.now())
                .build();

        shift = shiftRepository.save(shift);
        log.info("Opened shift {} for cashier {} at branch {}", shift.getId(), cashierId, request.getBranchId());
        return mapToShiftResponse(shift);
    }

    @Override
    @Transactional
    public PosShiftResponse closeShift(UUID shiftId, CloseShiftRequest request, UUID cashierId) {
        PosShift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new ResourceNotFoundException("Shift", "id", shiftId));

        if (!shift.getCashier().getId().equals(cashierId)) {
            throw new ValidationException("Only the shift owner can close this shift");
        }
        if (shift.getStatus() == PosShiftStatus.CLOSED) {
            throw new ValidationException("Shift is already closed");
        }

        // Calculate expected cash
        BigDecimal cashSales = saleRepository
                .sumTotalByBranchAndStatusAndDateRange(
                        shift.getBranch().getId(),
                        PosSaleStatus.COMPLETED,
                        shift.getOpenedAt(),
                        LocalDateTime.now());

        shift.setStatus(PosShiftStatus.CLOSED);
        shift.setClosingFloat(request.getClosingFloat());
        shift.setExpectedCash(shift.getOpeningFloat().add(cashSales != null ? cashSales : BigDecimal.ZERO));
        shift.setNotes(request.getNotes());
        shift.setClosedAt(LocalDateTime.now());

        return mapToShiftResponse(shiftRepository.save(shift));
    }

    @Override
    public ShiftReconciliationResponse getShiftReconciliation(UUID shiftId) {
        PosShift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new ResourceNotFoundException("Shift", "id", shiftId));

        LocalDateTime from = shift.getOpenedAt();
        LocalDateTime to = LocalDateTime.now();
        UUID branchId = shift.getBranch().getId();

        long totalTransactions = saleRepository.countByBranchAndStatusAndDateRange(
                branchId, PosSaleStatus.COMPLETED, from, to);

        BigDecimal totalSales = saleRepository.sumTotalByBranchAndStatusAndDateRange(
                branchId, PosSaleStatus.COMPLETED, from, to);
        totalSales = totalSales != null ? totalSales : BigDecimal.ZERO;

        List<Object[]> breakdown = paymentRepository
                .sumByPaymentMethodForBranchStatusAndDateRange(branchId, PosSaleStatus.COMPLETED, from, to);

        BigDecimal cashCollected = BigDecimal.ZERO;
        BigDecimal cardCollected = BigDecimal.ZERO;
        BigDecimal mpesaCollected = BigDecimal.ZERO;
        BigDecimal otherCollected = BigDecimal.ZERO;

        for (Object[] row : breakdown) {
            PosPaymentMethod method = (PosPaymentMethod) row[0];
            BigDecimal amount = (BigDecimal) row[1];
            if (amount == null) continue;
            switch (method) {
                case CASH -> cashCollected = cashCollected.add(amount);
                case CARD -> cardCollected = cardCollected.add(amount);
                case MPESA -> mpesaCollected = mpesaCollected.add(amount);
                default -> otherCollected = otherCollected.add(amount);
            }
        }

        BigDecimal expectedCash = shift.getOpeningFloat().add(cashCollected);

        return ShiftReconciliationResponse.builder()
                .shiftId(shift.getId())
                .cashierName(shift.getCashier().getFirstName() + " " + shift.getCashier().getLastName())
                .branchName(shift.getBranch().getName())
                .openedAt(shift.getOpenedAt())
                .openingFloat(shift.getOpeningFloat())
                .totalTransactions(totalTransactions)
                .totalSales(totalSales)
                .cashCollected(cashCollected)
                .cardCollected(cardCollected)
                .mpesaCollected(mpesaCollected)
                .otherCollected(otherCollected)
                .expectedCash(expectedCash)
                .build();
    }

    @Override
    public PosShiftResponse getCurrentShift(UUID branchId, UUID cashierId) {
        PosShift shift = shiftRepository.findByBranchIdAndCashierIdAndStatus(branchId, cashierId, PosShiftStatus.OPEN)
                .orElseThrow(() -> new ResourceNotFoundException("Open Shift", "branchId/cashierId", branchId + "/" + cashierId));
        return mapToShiftResponse(shift);
    }

    @Override
    @Transactional
    public PosSaleResponse createSale(CreateSaleRequest request, UUID cashierId) {
        DistributorBranch branch = branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Branch", "id", request.getBranchId()));

        User cashier = userRepository.findById(cashierId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", cashierId));

        PosShift shift = null;
        if (request.getShiftId() != null) {
            shift = shiftRepository.findById(request.getShiftId())
                    .orElseThrow(() -> new ResourceNotFoundException("Shift", "id", request.getShiftId()));
        }

        Customer customer = null;
        if (request.getCustomerId() != null) {
            customer = customerRepository.findById(request.getCustomerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", request.getCustomerId()));
        }

        PosSale sale = PosSale.builder()
                .branch(branch)
                .shift(shift)
                .cashier(cashier)
                .status(PosSaleStatus.UNPAID)
                .customer(customer)
                .customerName(customer != null ? customer.getBusinessName() : request.getCustomerName())
                .customerPhone(customer != null ? customer.getPhone() : request.getCustomerPhone())
                .notes(request.getNotes())
                .build();

        sale = saleRepository.save(sale);
        return mapToSaleResponse(sale);
    }

    @Override
    @Transactional
    public PosSaleResponse updateSaleItems(UUID saleId, UpdateSaleItemsRequest request) {
        PosSale sale = getSaleEntity(saleId);
        validateSaleUnpaid(sale);

        Warehouse warehouse = resolveWarehouse(sale.getBranch().getId(), sale.getBranch().getDistributor().getId());

        // Clear existing items (stock already deducted — not restored until cancellation)
        sale.getItems().clear();

        BigDecimal subtotal = BigDecimal.ZERO;

        for (SaleItemRequest itemReq : request.getItems()) {
            Product product = productRepository.findById(itemReq.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product", "id", itemReq.getProductId()));

            BigDecimal discount = itemReq.getDiscountAmount() != null ? itemReq.getDiscountAmount() : BigDecimal.ZERO;
            BigDecimal lineTotal = itemReq.getUnitPrice()
                    .multiply(itemReq.getQuantity())
                    .subtract(discount);

            PosSaleItem item = PosSaleItem.builder()
                    .sale(sale)
                    .product(product)
                    .productName(product.getName())
                    .productSku(product.getSku())
                    .quantity(itemReq.getQuantity())
                    .unitPrice(itemReq.getUnitPrice())
                    .discountAmount(discount)
                    .lineTotal(lineTotal)
                    .build();

            sale.getItems().add(item);
            subtotal = subtotal.add(lineTotal);

            // Deduct stock immediately
            if (warehouse != null) {
                adjustStock(warehouse, product, itemReq.getQuantity(), false);
            }
        }

        sale.setSubtotal(subtotal);
        sale.setTotalAmount(subtotal.subtract(sale.getDiscountAmount()).add(sale.getTaxAmount()));

        PosSale savedSale = saleRepository.save(sale);

        // Generate/update invoice immediately (SENT status until completed)
        try {
            invoiceService.createInvoiceFromPosSale(savedSale.getId());
        } catch (Exception e) {
            log.error("Failed to generate invoice for sale {}: {}", saleId, e.getMessage());
        }

        // Create or update the Order record immediately so it appears as UNPAID in the orders list
        try {
            upsertOrderFromPosSale(savedSale);
        } catch (Exception e) {
            log.error("Order upsert failed for POS sale {}: {}", saleId, e.getMessage(), e);
        }

        return mapToSaleResponse(savedSale);
    }

    @Override
    @Transactional
    public PosSaleResponse addPayment(UUID saleId, ProcessPaymentRequest request) {
        PosSale sale = getSaleEntity(saleId);
        validateSaleUnpaid(sale);

        PosSalePayment payment = PosSalePayment.builder()
                .sale(sale)
                .paymentMethod(request.getPaymentMethod())
                .amount(request.getAmount())
                .referenceNumber(request.getReferenceNumber())
                .notes(request.getNotes())
                .build();

        sale.getPayments().add(payment);

        BigDecimal totalPaid = sale.getPayments().stream()
                .map(PosSalePayment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        sale.setAmountPaid(totalPaid);
        BigDecimal change = totalPaid.subtract(sale.getTotalAmount());
        sale.setChangeGiven(change.compareTo(BigDecimal.ZERO) > 0 ? change : BigDecimal.ZERO);

        PosSale savedSale = saleRepository.save(sale);

        // GL: DR Cash / CR AR for this payment
        try {
            glAutoPostingService.postPosSalePayment(savedSale, payment.getAmount());
        } catch (Exception e) {
            log.warn("GL auto-post skipped (POS payment) for sale {}: {}", saleId, e.getMessage());
        }

        // Update the linked Order's paid amount and payment status
        try {
            orderRepository.findByPosSaleId(savedSale.getId()).ifPresent(order -> {
                order.setPaidAmount(totalPaid);
                order.setPaymentStatus(resolvePaymentStatus(totalPaid, savedSale.getTotalAmount()));
                orderRepository.save(order);
            });
        } catch (Exception e) {
            log.warn("Order payment status update failed for sale {}: {}", saleId, e.getMessage());
        }

        return mapToSaleResponse(savedSale);
    }

    @Override
    @Transactional
    public PosSaleResponse completeSale(UUID saleId, UUID warehouseId) {
        PosSale sale = getSaleEntity(saleId);
        validateSaleUnpaid(sale);

        if (sale.getItems().isEmpty()) {
            throw new ValidationException("Cannot complete a sale with no items");
        }
        // Allow underpayment (credit sales) — outstanding balance is tracked via invoice
        // Stock already deducted when items were set — nothing to do here

        sale.setStatus(PosSaleStatus.COMPLETED);
        sale.setReceiptNumber(generateReceiptNumber());
        sale.setCompletedAt(LocalDateTime.now());

        PosSale savedSale = saleRepository.save(sale);

        // Record payment transactions (one Payment record per tender type)
        paymentService.createPaymentsForPosSale(savedSale);

        // GL revenue + COGS were already posted when items were first set (accrual basis).
        // No new revenue entry here — completeSale only finalizes the order status.

        // Finalize the Order record (set status=DELIVERED, assign receipt number as order number)
        try {
            finalizeOrderFromPosSale(savedSale);
        } catch (Exception e) {
            log.error("Order finalization failed for POS sale {}: {}", savedSale.getReceiptNumber(), e.getMessage(), e);
        }

        // Publish event — invoice is created AFTER this transaction commits
        // (TransactionalEventListener AFTER_COMMIT), keeping invoice failure
        // fully isolated from the sale completion.
        eventPublisher.publishEvent(new PosSaleCompletedEvent(savedSale.getId()));

        return mapToSaleResponse(savedSale);
    }

    @Override
    @Transactional
    public PosSaleResponse cancelSale(UUID saleId, String reason) {
        PosSale sale = getSaleEntity(saleId);
        if (sale.getStatus() == PosSaleStatus.COMPLETED) {
            throw new ValidationException("Cannot cancel a completed sale. Use refund instead.");
        }
        if (sale.getStatus() == PosSaleStatus.CANCELLED) {
            throw new ValidationException("Sale is already cancelled");
        }

        // Restore stock for all items that were deducted when items were set
        if (!sale.getItems().isEmpty()) {
            Warehouse warehouse = resolveWarehouse(sale.getBranch().getId(), sale.getBranch().getDistributor().getId());
            if (warehouse != null) {
                for (PosSaleItem item : sale.getItems()) {
                    adjustStock(warehouse, item.getProduct(), item.getQuantity(), true);
                }
            }
        }

        sale.setStatus(PosSaleStatus.CANCELLED);
        sale.setNotes(reason);
        sale.setCancelledAt(LocalDateTime.now());

        return mapToSaleResponse(saleRepository.save(sale));
    }

    @Override
    @Transactional
    public PosSaleResponse refundSale(UUID saleId, UUID cashierId) {
        PosSale original = getSaleEntity(saleId);
        if (original.getStatus() != PosSaleStatus.COMPLETED) {
            throw new ValidationException("Can only refund completed sales");
        }

        User cashier = userRepository.findById(cashierId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", cashierId));

        PosSale refund = PosSale.builder()
                .branch(original.getBranch())
                .shift(original.getShift())
                .cashier(cashier)
                .status(PosSaleStatus.REFUNDED)
                .subtotal(original.getSubtotal().negate())
                .totalAmount(original.getTotalAmount().negate())
                .amountPaid(original.getAmountPaid().negate())
                .refundOf(original)
                .receiptNumber(generateReceiptNumber())
                .completedAt(LocalDateTime.now())
                .build();

        original.setStatus(PosSaleStatus.REFUNDED);
        saleRepository.save(original);

        return mapToSaleResponse(saleRepository.save(refund));
    }

    @Override
    public Page<PosSaleResponse> getSales(UUID branchId, String status, LocalDate startDate, LocalDate endDate, Pageable pageable) {
        UUID effectiveBranchId = securityUtils.getEffectiveBranchId();
        UUID filterBranchId = effectiveBranchId != null ? effectiveBranchId : branchId;

        LocalDateTime from = startDate != null ? startDate.atStartOfDay() : null;
        LocalDateTime to   = endDate   != null ? endDate.atTime(LocalTime.MAX) : null;
        boolean hasDate = from != null && to != null;

        PosSaleStatus saleStatus = (status != null && !status.isBlank())
                ? PosSaleStatus.valueOf(status.toUpperCase()) : null;

        if (filterBranchId == null) {
            if (hasDate && saleStatus != null)
                return saleRepository.findByStatusAndDateRange(saleStatus, from, to, pageable).map(this::mapToSaleResponse);
            if (hasDate)
                return saleRepository.findByCreatedAtBetween(from, to, pageable).map(this::mapToSaleResponse);
            if (saleStatus != null)
                return saleRepository.findByStatus(saleStatus, pageable).map(this::mapToSaleResponse);
            return saleRepository.findAll(pageable).map(this::mapToSaleResponse);
        }

        if (hasDate && saleStatus != null)
            return saleRepository.findByBranchIdAndStatusAndDateRange(filterBranchId, saleStatus, from, to, pageable).map(this::mapToSaleResponse);
        if (hasDate)
            return saleRepository.findByBranchIdAndCreatedAtBetween(filterBranchId, from, to, pageable).map(this::mapToSaleResponse);
        if (saleStatus != null)
            return saleRepository.findByBranchIdAndStatus(filterBranchId, saleStatus, pageable).map(this::mapToSaleResponse);
        return saleRepository.findByBranchId(filterBranchId, pageable).map(this::mapToSaleResponse);
    }

    @Override
    public PosSaleResponse getSaleById(UUID saleId) {
        return mapToSaleResponse(getSaleEntity(saleId));
    }

    @Override
    public PosSummaryResponse getDailySummary(UUID branchId, LocalDate startDate, LocalDate endDate) {
        DistributorBranch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch", "id", branchId));

        LocalDateTime from = startDate.atStartOfDay();
        LocalDateTime to = endDate.plusDays(1).atStartOfDay();

        long total = saleRepository.countByBranchAndStatusAndDateRange(branchId, PosSaleStatus.COMPLETED, from, to) +
                saleRepository.countByBranchAndStatusAndDateRange(branchId, PosSaleStatus.CANCELLED, from, to);
        long completed = saleRepository.countByBranchAndStatusAndDateRange(branchId, PosSaleStatus.COMPLETED, from, to);
        long cancelled = saleRepository.countByBranchAndStatusAndDateRange(branchId, PosSaleStatus.CANCELLED, from, to);
        BigDecimal revenue = saleRepository.sumTotalByBranchAndStatusAndDateRange(branchId, PosSaleStatus.COMPLETED, from, to);

        return PosSummaryResponse.builder()
                .branchId(branchId)
                .branchName(branch.getName())
                .date(startDate)
                .totalTransactions(total)
                .completedTransactions(completed)
                .cancelledTransactions(cancelled)
                .totalRevenue(revenue != null ? revenue : BigDecimal.ZERO)
                .totalDiscounts(BigDecimal.ZERO)
                .averageTransactionValue(completed > 0 && revenue != null ?
                        revenue.divide(BigDecimal.valueOf(completed), 2, java.math.RoundingMode.HALF_UP) :
                        BigDecimal.ZERO)
                .build();
    }

    // ---- Helpers ----

    /**
     * Creates or updates the Order linked to a POS sale with PENDING status and PENDING
     * payment status. Called from {@link #updateSaleItems} so the order appears in the
     * orders list immediately — before payment is recorded.
     */
    private void upsertOrderFromPosSale(PosSale sale) {
        BigDecimal totalPaid = sale.getAmountPaid() != null ? sale.getAmountPaid() : BigDecimal.ZERO;
        PaymentStatus paymentStatus = resolvePaymentStatus(totalPaid, sale.getTotalAmount());

        Order order = orderRepository.findByPosSaleId(sale.getId()).orElse(null);

        if (order == null) {
            // Temporary order number using sale ID; replaced by receipt number on completeSale
            String tempOrderNum = "POS-" + sale.getId().toString();
            order = Order.builder()
                    .orderNumber(tempOrderNum)
                    .distributor(sale.getBranch().getDistributor())
                    .merchant(sale.getCustomer())
                    .salesRep(sale.getCashier())
                    .orderType(OrderType.POS)
                    .status(OrderStatus.PENDING)
                    .paymentStatus(paymentStatus)
                    .subtotal(sale.getSubtotal())
                    .discountAmount(sale.getDiscountAmount())
                    .totalAmount(sale.getTotalAmount())
                    .paidAmount(totalPaid)
                    .notes(sale.getNotes())
                    .posSaleId(sale.getId())
                    .build();

            for (PosSaleItem saleItem : sale.getItems()) {
                OrderItem orderItem = OrderItem.builder()
                        .order(order).product(saleItem.getProduct())
                        .quantity(saleItem.getQuantity()).unitPrice(saleItem.getUnitPrice())
                        .totalAmount(saleItem.getLineTotal()).build();
                order.getItems().add(orderItem);
            }

            orderRepository.save(order);
            log.info("Created PENDING Order {} from POS sale {}", order.getOrderNumber(), sale.getId());

            // GL: DR AR / CR Revenue (accrual — revenue recognized when sale is created)
            try {
                glAutoPostingService.postPosSaleCompleted(sale);
            } catch (Exception e) {
                log.warn("GL auto-post skipped (POS revenue) for sale {}: {}", sale.getId(), e.getMessage());
            }

            // GL: DR COGS / CR Inventory — separate REQUIRES_NEW transaction to avoid
            // duplicate entry-number collision with the revenue entry above
            try {
                glAutoPostingService.postPosCogs(sale);
            } catch (Exception e) {
                log.warn("GL auto-post skipped (POS COGS) for sale {}: {}", sale.getId(), e.getMessage());
            }
        } else {
            // Update items and totals in case items were changed
            order.getItems().clear();
            for (PosSaleItem saleItem : sale.getItems()) {
                OrderItem orderItem = OrderItem.builder()
                        .order(order).product(saleItem.getProduct())
                        .quantity(saleItem.getQuantity()).unitPrice(saleItem.getUnitPrice())
                        .totalAmount(saleItem.getLineTotal()).build();
                order.getItems().add(orderItem);
            }
            order.setSubtotal(sale.getSubtotal());
            order.setDiscountAmount(sale.getDiscountAmount());
            order.setTotalAmount(sale.getTotalAmount());
            order.setPaidAmount(totalPaid);
            order.setPaymentStatus(paymentStatus);
            order.setMerchant(sale.getCustomer());

            orderRepository.save(order);
            log.info("Updated Order {} from POS sale {}", order.getOrderNumber(), sale.getId());
        }
    }

    /**
     * Marks the linked Order as DELIVERED (sale complete) and assigns the receipt number.
     * Falls back to creating a new Order if one doesn't exist yet.
     */
    private void finalizeOrderFromPosSale(PosSale sale) {
        BigDecimal totalPaid = sale.getAmountPaid() != null ? sale.getAmountPaid() : BigDecimal.ZERO;
        PaymentStatus paymentStatus = resolvePaymentStatus(totalPaid, sale.getTotalAmount());

        Order order = orderRepository.findByPosSaleId(sale.getId()).orElse(null);

        if (order == null) {
            // Fallback: create if upsertOrderFromPosSale was somehow not called
            order = Order.builder()
                    .orderNumber(sale.getReceiptNumber())
                    .distributor(sale.getBranch().getDistributor())
                    .merchant(sale.getCustomer())
                    .salesRep(sale.getCashier())
                    .orderType(OrderType.POS)
                    .status(OrderStatus.DELIVERED)
                    .paymentStatus(paymentStatus)
                    .subtotal(sale.getSubtotal())
                    .discountAmount(sale.getDiscountAmount())
                    .totalAmount(sale.getTotalAmount())
                    .paidAmount(totalPaid)
                    .notes(sale.getNotes())
                    .posSaleId(sale.getId())
                    .build();

            for (PosSaleItem saleItem : sale.getItems()) {
                OrderItem orderItem = OrderItem.builder()
                        .order(order).product(saleItem.getProduct())
                        .quantity(saleItem.getQuantity()).unitPrice(saleItem.getUnitPrice())
                        .totalAmount(saleItem.getLineTotal()).build();
                order.getItems().add(orderItem);
            }
        } else {
            // Update existing order to DELIVERED with final receipt number
            order.setOrderNumber(sale.getReceiptNumber());
            order.setStatus(OrderStatus.DELIVERED);
            order.setPaidAmount(totalPaid);
            order.setPaymentStatus(paymentStatus);
            order.setMerchant(sale.getCustomer());
        }

        orderRepository.save(order);
        log.info("Finalized Order {} from POS sale {}", order.getOrderNumber(), sale.getId());
    }

    private PaymentStatus resolvePaymentStatus(BigDecimal paid, BigDecimal total) {
        if (total == null || total.compareTo(BigDecimal.ZERO) == 0) return PaymentStatus.PENDING;
        if (paid.compareTo(total) >= 0) return PaymentStatus.PAID;
        if (paid.compareTo(BigDecimal.ZERO) > 0) return PaymentStatus.PARTIAL;
        return PaymentStatus.PENDING;
    }

    /** Returns the first active warehouse for the branch, falling back to distributor warehouse. */
    private Warehouse resolveWarehouse(UUID branchId, UUID distributorId) {
        List<Warehouse> branchWarehouses = warehouseRepository.findByBranchIdAndActiveTrue(branchId);
        if (!branchWarehouses.isEmpty()) return branchWarehouses.get(0);

        List<Warehouse> distributorWarehouses = warehouseRepository.findByDistributorIdAndActiveTrue(distributorId);
        if (!distributorWarehouses.isEmpty()) return distributorWarehouses.get(0);

        log.warn("No active warehouse found for branch {} / distributor {} — stock not adjusted", branchId, distributorId);
        return null;
    }

    /**
     * Adjusts stock for a product in a warehouse.
     * @param restore true = add back (cancel), false = deduct (sale)
     */
    private void adjustStock(Warehouse warehouse, Product product, BigDecimal quantity, boolean restore) {
        Stock stock = stockRepository.findByWarehouseIdAndProductId(warehouse.getId(), product.getId())
                .orElse(null);

        if (stock == null) {
            stock = Stock.builder()
                    .warehouse(warehouse)
                    .product(product)
                    .quantity(BigDecimal.ZERO)
                    .reservedQuantity(BigDecimal.ZERO)
                    .build();
        }

        if (restore) {
            stock.setQuantity(stock.getQuantity().add(quantity));
            log.info("Restored {} units of '{}' to warehouse {}", quantity, product.getName(), warehouse.getId());
        } else {
            stock.setQuantity(stock.getQuantity().subtract(quantity));
            log.info("Deducted {} units of '{}' from warehouse {}", quantity, product.getName(), warehouse.getId());
        }

        stockRepository.save(stock);
    }

    private PosSale getSaleEntity(UUID saleId) {
        return saleRepository.findById(saleId)
                .orElseThrow(() -> new ResourceNotFoundException("Sale", "id", saleId));
    }

    private void validateSaleUnpaid(PosSale sale) {
        if (sale.getStatus() != PosSaleStatus.UNPAID) {
            throw new ValidationException("Operation only allowed on UNPAID sales. Current status: " + sale.getStatus());
        }
    }

    private String generateReceiptNumber() {
        String prefix = "RCP-" + DateTimeFormatter.ofPattern("yyyyMMdd").format(LocalDateTime.now()) + "-";
        Integer maxNum = saleRepository.findMaxReceiptNumberByPrefix(prefix);
        int nextNum = (maxNum != null ? maxNum : 0) + 1;
        return prefix + String.format("%05d", nextNum);
    }

    private PosTerminalResponse mapToTerminalResponse(PosTerminal terminal) {
        return PosTerminalResponse.builder()
                .id(terminal.getId())
                .branchId(terminal.getBranch().getId())
                .branchName(terminal.getBranch().getName())
                .name(terminal.getName())
                .code(terminal.getCode())
                .status(terminal.getStatus())
                .createdAt(terminal.getCreatedAt())
                .build();
    }

    private PosShiftResponse mapToShiftResponse(PosShift shift) {
        return PosShiftResponse.builder()
                .id(shift.getId())
                .branchId(shift.getBranch().getId())
                .branchName(shift.getBranch().getName())
                .terminalId(shift.getTerminal() != null ? shift.getTerminal().getId() : null)
                .terminalName(shift.getTerminal() != null ? shift.getTerminal().getName() : null)
                .cashierId(shift.getCashier().getId())
                .cashierName(shift.getCashier().getFirstName() + " " + shift.getCashier().getLastName())
                .status(shift.getStatus())
                .openingFloat(shift.getOpeningFloat())
                .closingFloat(shift.getClosingFloat())
                .expectedCash(shift.getExpectedCash())
                .notes(shift.getNotes())
                .openedAt(shift.getOpenedAt())
                .closedAt(shift.getClosedAt())
                .createdAt(shift.getCreatedAt())
                .build();
    }

    private PosSaleResponse mapToSaleResponse(PosSale sale) {
        List<PosSaleItemResponse> items = sale.getItems().stream()
                .map(i -> PosSaleItemResponse.builder()
                        .id(i.getId())
                        .productId(i.getProduct().getId())
                        .productName(i.getProductName())
                        .productSku(i.getProductSku())
                        .quantity(i.getQuantity())
                        .unitPrice(i.getUnitPrice())
                        .discountAmount(i.getDiscountAmount())
                        .lineTotal(i.getLineTotal())
                        .build())
                .collect(Collectors.toList());

        List<PosSalePaymentResponse> payments = sale.getPayments().stream()
                .map(p -> PosSalePaymentResponse.builder()
                        .id(p.getId())
                        .paymentMethod(p.getPaymentMethod())
                        .amount(p.getAmount())
                        .referenceNumber(p.getReferenceNumber())
                        .notes(p.getNotes())
                        .createdAt(p.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return PosSaleResponse.builder()
                .id(sale.getId())
                .branchId(sale.getBranch().getId())
                .branchName(sale.getBranch().getName())
                .shiftId(sale.getShift() != null ? sale.getShift().getId() : null)
                .cashierId(sale.getCashier().getId())
                .cashierName(sale.getCashier().getFirstName() + " " + sale.getCashier().getLastName())
                .receiptNumber(sale.getReceiptNumber())
                .status(sale.getStatus())
                .subtotal(sale.getSubtotal())
                .discountAmount(sale.getDiscountAmount())
                .taxAmount(sale.getTaxAmount())
                .totalAmount(sale.getTotalAmount())
                .amountPaid(sale.getAmountPaid())
                .changeGiven(sale.getChangeGiven())
                .customerId(sale.getCustomer() != null ? sale.getCustomer().getId() : null)
                .customerBusinessName(sale.getCustomer() != null ? sale.getCustomer().getBusinessName() : null)
                .customerName(sale.getCustomerName())
                .customerPhone(sale.getCustomerPhone())
                .notes(sale.getNotes())
                .items(items)
                .payments(payments)
                .completedAt(sale.getCompletedAt())
                .cancelledAt(sale.getCancelledAt())
                .createdAt(sale.getCreatedAt())
                .updatedAt(sale.getUpdatedAt())
                .build();
    }

    // ── Shift Reconciliation ──────────────────────────────────────────────────

    @Override
    public ShiftReconciliationResponse getShiftReconciliation(UUID shiftId) {
        PosShift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new ResourceNotFoundException("PosShift", "id", shiftId));

        // Collect all payments for completed sales in this shift
        List<PosSalePayment> payments = paymentRepository.findByShiftId(shiftId);

        BigDecimal cashCollected  = BigDecimal.ZERO;
        BigDecimal cardCollected  = BigDecimal.ZERO;
        BigDecimal mpesaCollected = BigDecimal.ZERO;
        BigDecimal otherCollected = BigDecimal.ZERO;

        for (PosSalePayment p : payments) {
            BigDecimal amt = p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO;
            switch (p.getPaymentMethod()) {
                case CASH          -> cashCollected  = cashCollected.add(amt);
                case CARD          -> cardCollected  = cardCollected.add(amt);
                case MPESA         -> mpesaCollected = mpesaCollected.add(amt);
                default            -> otherCollected = otherCollected.add(amt);
            }
        }

        // Count completed sales
        Page<PosSale> salesPage = saleRepository.findByShiftId(shiftId,
                org.springframework.data.domain.Pageable.unpaged());
        long completedCount = salesPage.stream()
                .filter(s -> PosSaleStatus.COMPLETED.equals(s.getStatus()))
                .count();
        BigDecimal totalSales = salesPage.stream()
                .filter(s -> PosSaleStatus.COMPLETED.equals(s.getStatus()))
                .map(PosSale::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal openingFloat = shift.getOpeningFloat() != null
                ? shift.getOpeningFloat() : BigDecimal.ZERO;

        String cashierName = shift.getCashier() != null
                ? (shift.getCashier().getFirstName() + " " + shift.getCashier().getLastName()).trim()
                : "Unknown";
        String branchName = shift.getBranch() != null
                ? shift.getBranch().getName() : "Unknown";

        return ShiftReconciliationResponse.builder()
                .shiftId(shift.getId())
                .cashierName(cashierName)
                .branchName(branchName)
                .openedAt(shift.getOpenedAt())
                .openingFloat(openingFloat)
                .totalTransactions((int) completedCount)
                .totalSales(totalSales)
                .cashCollected(cashCollected)
                .cardCollected(cardCollected)
                .mpesaCollected(mpesaCollected)
                .otherCollected(otherCollected)
                .expectedCash(openingFloat.add(cashCollected))
                .build();
    }
}
