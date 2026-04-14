package com.zuqi.service.impl;

import com.zuqi.api.dto.approval.CreateApprovalRequestDto;
import com.zuqi.api.dto.returns.*;
import com.zuqi.domain.approval.ApprovalWorkflowType;
import com.zuqi.domain.customer.Customer;
import com.zuqi.domain.distributor.Distributor;
import com.zuqi.domain.inventory.Stock;
import com.zuqi.domain.inventory.StockMovement;
import com.zuqi.domain.inventory.Warehouse;
import com.zuqi.domain.invoice.Invoice;
import com.zuqi.domain.invoice.InvoiceStatus;
import com.zuqi.domain.order.Order;
import com.zuqi.domain.product.Product;
import com.zuqi.domain.returns.*;
import com.zuqi.domain.user.User;
import com.zuqi.exception.ResourceNotFoundException;
import com.zuqi.exception.ValidationException;
import com.zuqi.repository.*;
import com.zuqi.service.ApprovalService;
import com.zuqi.service.SalesReturnService;
import com.zuqi.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SalesReturnServiceImpl implements SalesReturnService {

    private final SalesReturnRepository    salesReturnRepository;
    private final OrderRepository          orderRepository;
    private final CustomerRepository       customerRepository;
    private final ProductRepository        productRepository;
    private final UserRepository           userRepository;
    private final DistributorRepository    distributorRepository;
    private final InvoiceRepository        invoiceRepository;
    private final StockRepository          stockRepository;
    private final StockMovementRepository  stockMovementRepository;
    private final WarehouseRepository      warehouseRepository;
    private final SecurityUtils            securityUtils;

    @Lazy @Autowired
    private ApprovalService approvalService;

    @Override
    @Transactional
    public SalesReturnResponse create(CreateSalesReturnRequest request, UUID createdById) {

        // Resolve distributor
        UUID distId = securityUtils.getDistributorIdForFiltering();
        Distributor distributor = null;
        if (distId != null) {
            distributor = distributorRepository.findById(distId)
                    .orElseThrow(() -> new ResourceNotFoundException("Distributor", "id", distId));
        } else if (request.getOrderId() != null) {
            Order linkedOrder = orderRepository.findById(request.getOrderId())
                    .orElseThrow(() -> new ResourceNotFoundException("Order", "id", request.getOrderId()));
            distributor = linkedOrder.getDistributor();
        } else if (request.getInvoiceId() != null) {
            Invoice linkedInvoice = invoiceRepository.findById(request.getInvoiceId())
                    .orElseThrow(() -> new ResourceNotFoundException("Invoice", "id", request.getInvoiceId()));
            distributor = linkedInvoice.getDistributor();
        }
        if (distributor == null) throw new ValidationException("Cannot determine distributor for return");

        Order order = request.getOrderId() != null
                ? orderRepository.findById(request.getOrderId())
                        .orElseThrow(() -> new ResourceNotFoundException("Order", "id", request.getOrderId()))
                : null;

        // Resolve invoice; if no orderId but invoiceId, derive order from invoice
        Invoice invoice = null;
        if (request.getInvoiceId() != null) {
            invoice = invoiceRepository.findById(request.getInvoiceId())
                    .orElseThrow(() -> new ResourceNotFoundException("Invoice", "id", request.getInvoiceId()));
            if (order == null && invoice.getOrder() != null) {
                order = invoice.getOrder();
            }
        }

        Customer customer = request.getCustomerId() != null
                ? customerRepository.findById(request.getCustomerId())
                        .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", request.getCustomerId()))
                : null;

        User createdBy = createdById != null
                ? userRepository.findById(createdById).orElse(null)
                : null;

        SalesReturn sr = SalesReturn.builder()
                .returnNumber(generateNumber("SR"))
                .distributor(distributor)
                .order(order)
                .invoice(invoice)
                .customer(customer)
                .reason(request.getReason())
                .refundMethod(request.getRefundMethod())
                .status(ReturnStatus.DRAFT)
                .totalAmount(BigDecimal.ZERO)
                .createdBy(createdBy)
                .build();

        final Distributor finalDistributor = distributor;
        List<SalesReturnItem> items = request.getItems().stream().map(line -> {
            Product product = productRepository.findById(line.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product", "id", line.getProductId()));
            BigDecimal total = line.getUnitPrice().multiply(line.getQuantity());
            return SalesReturnItem.builder()
                    .salesReturn(sr)
                    .product(product)
                    .quantity(line.getQuantity())
                    .unitPrice(line.getUnitPrice())
                    .totalAmount(total)
                    .reason(line.getReason())
                    .build();
        }).collect(Collectors.toList());

        sr.setItems(items);
        sr.setTotalAmount(items.stream().map(SalesReturnItem::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add));

        SalesReturn saved = salesReturnRepository.save(sr);

        // Approval routing: if current user requires approval for RETURNS, queue instead of leaving as DRAFT
        UUID currentUserId = securityUtils.getCurrentUserId();
        if (securityUtils.currentUserRequiresApprovalFor("RETURNS") && currentUserId != null) {
            saved.setStatus(ReturnStatus.PENDING_APPROVAL);
            saved = salesReturnRepository.save(saved);
            approvalService.createRequest(currentUserId, CreateApprovalRequestDto.builder()
                    .workflowType(ApprovalWorkflowType.SALES_RETURN)
                    .entityType("SALES_RETURN")
                    .entityId(saved.getId())
                    .entityName(saved.getReturnNumber())
                    .description("Sales Return " + saved.getReturnNumber()
                            + (customer != null ? " for " + customer.getBusinessName() : "")
                            + " — total KES " + saved.getTotalAmount())
                    .amount(saved.getTotalAmount())
                    .requiredApprovals(1)
                    .build());
            log.info("Sales return {} queued for approval", saved.getReturnNumber());
        }

        return toResponse(saved);
    }

    @Override
    @Transactional
    public SalesReturnResponse confirm(UUID id) {
        SalesReturn sr = findOrThrow(id);
        if (sr.getStatus() != ReturnStatus.DRAFT) {
            throw new ValidationException("Only DRAFT returns can be confirmed");
        }
        sr.setStatus(ReturnStatus.CONFIRMED);
        SalesReturn saved = salesReturnRepository.save(sr);

        // Restore stock for each returned item
        Warehouse warehouse = resolveWarehouse(sr);
        if (warehouse != null) {
            for (SalesReturnItem item : sr.getItems()) {
                Product product = item.getProduct();
                Stock stock = stockRepository.findByWarehouseIdAndProductId(warehouse.getId(), product.getId())
                        .orElseGet(() -> Stock.builder()
                                .warehouse(warehouse)
                                .product(product)
                                .quantity(BigDecimal.ZERO)
                                .build());
                stock.setQuantity(stock.getQuantity().add(item.getQuantity()));
                stockRepository.save(stock);

                StockMovement movement = StockMovement.builder()
                        .warehouse(warehouse)
                        .product(product)
                        .movementType(StockMovement.MovementType.IN)
                        .quantity(item.getQuantity())
                        .referenceType("RETURN")
                        .referenceId(sr.getId())
                        .notes("Sales return " + sr.getReturnNumber())
                        .build();
                stockMovementRepository.save(movement);

                log.info("Restored {} units of '{}' to warehouse '{}' via return {}",
                        item.getQuantity(), product.getName(), warehouse.getName(), sr.getReturnNumber());
            }
        } else {
            log.warn("No warehouse found for return {} — stock not restored", sr.getReturnNumber());
        }

        // Credit the return amount against the linked invoice
        if (sr.getInvoice() != null) {
            Invoice invoice = sr.getInvoice();
            InvoiceStatus currentStatus = invoice.getStatus();
            // Only adjust invoices that still have an open balance
            if (currentStatus != InvoiceStatus.PAID
                    && currentStatus != InvoiceStatus.CANCELLED
                    && currentStatus != InvoiceStatus.VOID) {
                invoice.recordPayment(sr.getTotalAmount());
                invoiceRepository.save(invoice);
                log.info("Invoice {} balance reduced by KES {} due to confirmed return {}",
                        invoice.getInvoiceNumber(), sr.getTotalAmount(), sr.getReturnNumber());
            }
        }

        return toResponse(saved);
    }

    @Override
    @Transactional
    public SalesReturnResponse cancel(UUID id) {
        SalesReturn sr = findOrThrow(id);
        if (sr.getStatus() == ReturnStatus.CONFIRMED) {
            throw new ValidationException("Confirmed returns cannot be cancelled");
        }
        sr.setStatus(ReturnStatus.CANCELLED);
        return toResponse(salesReturnRepository.save(sr));
    }

    @Override
    public SalesReturnResponse getById(UUID id) {
        return toResponse(findOrThrow(id));
    }

    @Override
    public Page<SalesReturnResponse> getAll(Pageable pageable) {
        UUID distId = securityUtils.getDistributorIdForFiltering();
        UUID merchantId = securityUtils.getCurrentUserMerchantId();
        if (distId != null) {
            return salesReturnRepository.findByDistributorId(distId, pageable).map(this::toResponse);
        } else if (merchantId != null) {
            return salesReturnRepository.findByDistributorMerchantId(merchantId, pageable).map(this::toResponse);
        }
        return salesReturnRepository.findAll(pageable).map(this::toResponse);
    }

    private SalesReturn findOrThrow(UUID id) {
        return salesReturnRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SalesReturn", "id", id));
    }

    /** Returns the warehouse to use for stock restoration: order warehouse → fallback to first distributor warehouse. */
    private Warehouse resolveWarehouse(SalesReturn sr) {
        // Prefer order's warehouse
        if (sr.getOrder() != null && sr.getOrder().getWarehouse() != null) {
            return sr.getOrder().getWarehouse();
        }
        // Try invoice → order → warehouse
        if (sr.getInvoice() != null && sr.getInvoice().getOrder() != null
                && sr.getInvoice().getOrder().getWarehouse() != null) {
            return sr.getInvoice().getOrder().getWarehouse();
        }
        // Fallback: first active warehouse for the distributor
        if (sr.getDistributor() != null) {
            List<Warehouse> warehouses = warehouseRepository.findByDistributorIdAndActiveTrue(sr.getDistributor().getId());
            return warehouses.isEmpty() ? null : warehouses.get(0);
        }
        return null;
    }

    private String generateNumber(String prefix) {
        return prefix + "-" + System.currentTimeMillis();
    }

    private SalesReturnResponse toResponse(SalesReturn sr) {
        List<SalesReturnResponse.ItemResponse> items = sr.getItems().stream()
                .map(i -> SalesReturnResponse.ItemResponse.builder()
                        .id(i.getId())
                        .productId(i.getProduct().getId())
                        .productName(i.getProduct().getName())
                        .quantity(i.getQuantity())
                        .unitPrice(i.getUnitPrice())
                        .totalAmount(i.getTotalAmount())
                        .reason(i.getReason())
                        .build())
                .collect(Collectors.toList());
        return SalesReturnResponse.builder()
                .id(sr.getId())
                .returnNumber(sr.getReturnNumber())
                .distributorId(sr.getDistributor().getId())
                .orderId(sr.getOrder() != null ? sr.getOrder().getId() : null)
                .invoiceId(sr.getInvoice() != null ? sr.getInvoice().getId() : null)
                .invoiceNumber(sr.getInvoice() != null ? sr.getInvoice().getInvoiceNumber() : null)
                .customerId(sr.getCustomer() != null ? sr.getCustomer().getId() : null)
                .customerName(sr.getCustomer() != null ? sr.getCustomer().getBusinessName() : null)
                .reason(sr.getReason())
                .status(sr.getStatus().name())
                .totalAmount(sr.getTotalAmount())
                .refundMethod(sr.getRefundMethod())
                .items(items)
                .createdAt(sr.getCreatedAt())
                .updatedAt(sr.getUpdatedAt())
                .build();
    }
}
