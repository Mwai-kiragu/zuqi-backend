package com.zuqi.service.impl;

import com.zuqi.api.dto.invoice.InvoiceResponse;
import com.zuqi.api.dto.invoice.ManualInvoiceItemRequest;
import com.zuqi.api.dto.invoice.ManualInvoiceRequest;
import com.zuqi.config.EmailConfig;
import com.zuqi.domain.customer.Customer;
import com.zuqi.domain.distributor.Distributor;
import com.zuqi.domain.inventory.Stock;
import com.zuqi.domain.inventory.Warehouse;
import com.zuqi.domain.invoice.Invoice;
import com.zuqi.domain.invoice.InvoiceItem;
import com.zuqi.domain.invoice.InvoiceStatus;
import com.zuqi.domain.order.Order;
import com.zuqi.domain.order.OrderItem;
import com.zuqi.domain.payment.PaymentStatus;
import com.zuqi.domain.pos.PosSale;
import com.zuqi.domain.pos.PosSaleStatus;
import com.zuqi.domain.product.Product;
import com.zuqi.exception.ResourceNotFoundException;
import com.zuqi.exception.ValidationException;
import com.zuqi.api.dto.payment.PaymentRequest;
import com.zuqi.repository.CustomerRepository;
import com.zuqi.repository.DistributorRepository;
import com.zuqi.repository.InvoiceItemRepository;
import com.zuqi.repository.InvoiceRepository;
import com.zuqi.repository.PosSaleRepository;
import com.zuqi.repository.ProductRepository;
import com.zuqi.repository.StockRepository;
import com.zuqi.repository.WarehouseRepository;
import com.zuqi.api.dto.approval.CreateApprovalRequestDto;
import com.zuqi.domain.approval.ApprovalWorkflowType;
import com.zuqi.service.ApprovalService;
import com.zuqi.service.EmailService;
import com.zuqi.service.GlAutoPostingService;
import com.zuqi.service.InvoiceService;
import com.zuqi.service.PaymentService;
import com.zuqi.util.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class InvoiceServiceImpl implements InvoiceService {

    @Value("${app.frontend.url}")
    private String frontendUrl;

    private final InvoiceRepository invoiceRepository;
    private final InvoiceItemRepository invoiceItemRepository;
    private final PosSaleRepository posSaleRepository;
    private final CustomerRepository customerRepository;
    private final DistributorRepository distributorRepository;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final StockRepository stockRepository;
    private final EmailService emailService;
    private final EmailConfig emailConfig;
    private final SecurityUtils securityUtils;
    private final PaymentService paymentService;
    private final GlAutoPostingService glAutoPostingService;

    @Lazy @Autowired
    private ApprovalService approvalService;

    @Override
    @Transactional
    public InvoiceResponse createManualInvoice(ManualInvoiceRequest request) {
        log.info("Creating manual invoice for customer: {}", request.getCustomerId());

        // Resolve distributor
        UUID effectiveDistributorId = request.getDistributorId();
        if (effectiveDistributorId == null) {
            effectiveDistributorId = securityUtils.getDistributorIdForFiltering();
        }
        if (effectiveDistributorId == null) {
            throw new ValidationException("distributorId is required to create a manual invoice");
        }

        final UUID resolvedDistributorId = effectiveDistributorId;
        Distributor distributor = distributorRepository.findById(resolvedDistributorId)
                .orElseThrow(() -> new ResourceNotFoundException("Distributor", "id", resolvedDistributorId.toString()));

        Customer customer = customerRepository.findById(request.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", request.getCustomerId().toString()));

        // Build invoice items and compute subtotal
        List<InvoiceItem> items = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;

        for (ManualInvoiceItemRequest itemReq : request.getItems()) {
            Product product = productRepository.findById(itemReq.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product", "id", itemReq.getProductId().toString()));

            BigDecimal unitPrice = itemReq.getUnitPrice() != null ? itemReq.getUnitPrice() : product.getUnitPrice();

            InvoiceItem item = InvoiceItem.builder()
                    .product(product)
                    .description(product.getName())
                    .quantity(itemReq.getQuantity())
                    .unitPrice(unitPrice)
                    .discountPercent(itemReq.getDiscountPercent() != null ? itemReq.getDiscountPercent() : BigDecimal.ZERO)
                    .build();
            item.calculateTotal();
            items.add(item);
            subtotal = subtotal.add(item.getTotalAmount());
        }

        BigDecimal discount = request.getDiscountAmount() != null ? request.getDiscountAmount() : BigDecimal.ZERO;
        BigDecimal tax = request.getTaxAmount() != null ? request.getTaxAmount() : BigDecimal.ZERO;
        BigDecimal total = subtotal.subtract(discount).add(tax);

        int termsDays = request.getPaymentTermsDays() != null ? request.getPaymentTermsDays()
                : (customer.getPaymentTermsDays() != null ? customer.getPaymentTermsDays() : 30);

        Invoice invoice = Invoice.builder()
                .invoiceNumber(generateInvoiceNumber(distributor.getId(), distributor.getName()))
                .sourceType("MANUAL")
                .distributor(distributor)
                .merchant(customer)
                .subtotal(subtotal)
                .amount(total)
                .discountAmount(discount)
                .taxAmount(tax)
                .totalAmount(total)
                .issueDate(LocalDate.now())
                .dueDate(LocalDate.now().plusDays(Math.max(termsDays, 1)))
                .notes(request.getNotes())
                .termsAndConditions(request.getTermsAndConditions())
                .build();

        invoice.calculateBalanceDue();
        Invoice saved = invoiceRepository.save(invoice);

        // Link items to the saved invoice and persist
        for (InvoiceItem item : items) {
            item.setInvoice(saved);
        }
        invoiceItemRepository.saveAll(items);
        saved.setInvoiceItems(items);

        // Approval routing: if user requires approval for INVOICES, leave invoice as DRAFT and queue
        UUID currentUserId = securityUtils.getCurrentUserId();
        if (securityUtils.currentUserRequiresApprovalFor("INVOICES") && currentUserId != null) {
            approvalService.createRequest(currentUserId, CreateApprovalRequestDto.builder()
                    .workflowType(ApprovalWorkflowType.INVOICE_CREATION)
                    .entityType("INVOICE")
                    .entityId(saved.getId())
                    .entityName(saved.getInvoiceNumber())
                    .description("Manual invoice " + saved.getInvoiceNumber() + " for " + customer.getBusinessName()
                            + " — total KES " + saved.getTotalAmount())
                    .amount(saved.getTotalAmount())
                    .requiredApprovals(1)
                    .build());
            log.info("Invoice {} queued for approval (status: DRAFT)", saved.getInvoiceNumber());
            return InvoiceResponse.fromEntity(saved);
        }

        // No approval needed — activate immediately
        saved.setStatus(InvoiceStatus.UNPAID);
        final Invoice activatedInvoice = invoiceRepository.save(saved);
        saved = activatedInvoice;

        // Deduct stock if warehouse specified
        if (request.getWarehouseId() != null) {
            warehouseRepository.findById(request.getWarehouseId()).ifPresent(warehouse -> {
                for (InvoiceItem item : items) {
                    if (item.getProduct() != null) {
                        stockRepository.findByWarehouseIdAndProductId(warehouse.getId(), item.getProduct().getId())
                                .ifPresent(stock -> {
                                    stock.setQuantity(stock.getQuantity().subtract(BigDecimal.valueOf(item.getQuantity())));
                                    stockRepository.save(stock);
                                    log.info("Deducted {} of '{}' for manual invoice {}",
                                            item.getQuantity(), item.getProduct().getName(), activatedInvoice.getInvoiceNumber());
                                });
                    }
                }
            });
        }

        // GL auto-posting: DR Accounts Receivable / CR Sales Revenue (non-blocking)
        try {
            glAutoPostingService.postInvoiceCreated(saved);
        } catch (Exception e) {
            log.warn("GL auto-posting failed for manual invoice {}: {}", saved.getInvoiceNumber(), e.getMessage());
        }

        // GL COGS posting: DR Cost of Goods Sold / CR Inventory (non-blocking)
        try {
            glAutoPostingService.postManualInvoiceCogs(saved, items, resolvedDistributorId);
        } catch (Exception e) {
            log.warn("GL COGS posting failed for manual invoice {}: {}", saved.getInvoiceNumber(), e.getMessage());
        }

        // Auto-send email if customer has an email address
        if (customer.getEmail() != null && !customer.getEmail().isBlank()) {
            try {
                sendInvoiceEmailAsync(saved, customer.getEmail());
                saved.markAsSent(customer.getEmail());
                invoiceRepository.save(saved);
            } catch (Exception e) {
                log.warn("Failed to send manual invoice email to {}: {}", customer.getEmail(), e.getMessage());
            }
        }

        return InvoiceResponse.fromEntity(saved);
    }

    @Override
    @Transactional
    public InvoiceResponse createInvoiceFromOrder(Order order) {
        log.info("Creating invoice for order: {}", order.getOrderNumber());

        // Every invoice must be tied to a customer record
        if (order.getMerchant() == null) {
            throw new ValidationException("Invoice cannot be created: order " + order.getOrderNumber() + " has no customer assigned.");
        }

        // Check if invoice already exists for this order
        if (invoiceRepository.findByOrderId(order.getId()).isPresent()) {
            throw new ValidationException("Invoice already exists for order: " + order.getOrderNumber());
        }

        // Generate invoice number
        String invoiceNumber = generateInvoiceNumber(order.getDistributor().getId(), order.getDistributor().getName());

        // Calculate due date based on payment terms
        LocalDate dueDate = LocalDate.now();
        if (order.getPaymentTermsDays() != null && order.getPaymentTermsDays() > 0) {
            dueDate = dueDate.plusDays(order.getPaymentTermsDays());
        }

        // Create invoice
        Invoice invoice = Invoice.builder()
                .invoiceNumber(invoiceNumber)
                .order(order)
                .distributor(order.getDistributor())
                .merchant(order.getMerchant())
                .amount(order.getTotalAmount())
                .subtotal(order.getSubtotal())
                .discountAmount(order.getDiscountAmount())
                .taxAmount(order.getTaxAmount() != null ? order.getTaxAmount() : BigDecimal.ZERO)
                .totalAmount(order.getTotalAmount())
                .paidAmount(order.getPaidAmount() != null ? order.getPaidAmount() : BigDecimal.ZERO)
                .status(InvoiceStatus.DRAFT)
                .issueDate(LocalDate.now())
                .dueDate(dueDate)
                .recipientEmail(order.getMerchant().getEmail())
                .build();

        invoice.calculateBalanceDue();
        invoice = invoiceRepository.save(invoice);

        log.info("Invoice created successfully: {}", invoice.getInvoiceNumber());

        // Auto-post to GL: DR Accounts Receivable / CR Sales Revenue
        try {
            glAutoPostingService.postInvoiceCreated(invoice);
        } catch (Exception e) {
            log.warn("GL auto-post skipped (invoice created) for {}: {}", invoice.getInvoiceNumber(), e.getMessage());
        }

        // Automatically send invoice if merchant has email
        if (order.getMerchant().getEmail() != null && !order.getMerchant().getEmail().isEmpty()) {
            sendInvoiceEmailAsync(invoice, order.getMerchant().getEmail());
            // Mark as sent
            invoice.markAsSent(order.getMerchant().getEmail());
            invoice = invoiceRepository.save(invoice);
        }

        return InvoiceResponse.fromEntity(invoice);
    }

    @Override
    @Transactional
    public InvoiceResponse createInvoiceFromPosSale(UUID saleId) {
        log.info("Syncing invoice for POS sale: {}", saleId);

        PosSale sale = posSaleRepository.findById(saleId)
                .orElseThrow(() -> new ResourceNotFoundException("PosSale", "id", saleId));

        // Invoice requires a customer — skip creation for walk-in (no-account) sales
        if (sale.getCustomer() == null) {
            log.info("Skipping invoice creation for POS sale {} — no customer account linked", saleId);
            return null;
        }

        boolean isCompleted = sale.getStatus() == PosSaleStatus.COMPLETED;
        BigDecimal paidAmount = isCompleted ? (sale.getAmountPaid() != null ? sale.getAmountPaid() : BigDecimal.ZERO) : BigDecimal.ZERO;
        BigDecimal total = sale.getTotalAmount() != null ? sale.getTotalAmount() : BigDecimal.ZERO;
        InvoiceStatus status;
        if (!isCompleted) {
            status = InvoiceStatus.UNPAID;
        } else if (paidAmount.compareTo(total) >= 0) {
            status = InvoiceStatus.PAID;
        } else if (paidAmount.compareTo(BigDecimal.ZERO) > 0) {
            status = InvoiceStatus.PARTIALLY_PAID;
        } else {
            status = InvoiceStatus.UNPAID;
        }

        // Upsert: update existing invoice if found
        Optional<Invoice> existing = invoiceRepository.findByPosOrderId(saleId);
        if (existing.isPresent()) {
            Invoice invoice = existing.get();
            if (invoice.getMerchant() == null && sale.getCustomer() != null) {
                invoice.setMerchant(sale.getCustomer());
            }
            invoice.setAmount(sale.getTotalAmount());
            invoice.setSubtotal(sale.getSubtotal());
            invoice.setDiscountAmount(sale.getDiscountAmount());
            invoice.setTaxAmount(sale.getTaxAmount());
            invoice.setTotalAmount(sale.getTotalAmount());
            invoice.setPaidAmount(paidAmount);
            invoice.setStatus(status);
            invoice.calculateBalanceDue();
            Invoice savedInvoice = invoiceRepository.save(invoice);
            log.info("POS invoice updated: {}", savedInvoice.getInvoiceNumber());

            // Send PAID receipt email when sale is completed and customer has email
            if (isCompleted && sale.getCustomer() != null && sale.getCustomer().getEmail() != null) {
                sendPosReceiptEmail(savedInvoice, sale, true);
            }

            return InvoiceResponse.fromEntity(savedInvoice);
        }

        // Create new invoice
        String invoiceNumber = generateInvoiceNumber(sale.getBranch().getDistributor().getId(), sale.getBranch().getDistributor().getName());
        Invoice invoice = Invoice.builder()
                .invoiceNumber(invoiceNumber)
                .sourceType("POS_SALE")
                .posOrder(sale)
                .distributor(sale.getBranch().getDistributor())
                .merchant(sale.getCustomer())
                .amount(sale.getTotalAmount())
                .subtotal(sale.getSubtotal())
                .discountAmount(sale.getDiscountAmount())
                .taxAmount(sale.getTaxAmount())
                .totalAmount(sale.getTotalAmount())
                .paidAmount(paidAmount)
                .status(status)
                .issueDate(LocalDate.now())
                .dueDate(isCompleted ? LocalDate.now() : LocalDate.now().plusDays(30))
                .build();

        invoice.calculateBalanceDue();
        invoice = invoiceRepository.save(invoice);

        // Send receipt email to the customer immediately (e.g. when cashier clicks "Print Bill")
        if (sale.getCustomer() != null && sale.getCustomer().getEmail() != null) {
            sendPosReceiptEmail(invoice, sale, isCompleted);
        }

        log.info("POS invoice created: {} ({})", invoice.getInvoiceNumber(), status);
        return InvoiceResponse.fromEntity(invoice);
    }

    @Override
    public InvoiceResponse getInvoiceBySaleId(UUID saleId) {
        Invoice invoice = invoiceRepository.findByPosOrderId(saleId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", "posOrderId", saleId));
        return InvoiceResponse.fromEntity(invoice);
    }

    @Override
    public InvoiceResponse getInvoiceById(UUID id) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", "id", id));
        return InvoiceResponse.fromEntity(invoice);
    }

    @Override
    public InvoiceResponse getInvoiceByNumber(String invoiceNumber) {
        Invoice invoice = invoiceRepository.findByInvoiceNumber(invoiceNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", "invoiceNumber", invoiceNumber));
        return InvoiceResponse.fromEntity(invoice);
    }

    @Override
    public InvoiceResponse getInvoiceByOrderId(UUID orderId) {
        Invoice invoice = invoiceRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", "orderId", orderId));
        return InvoiceResponse.fromEntity(invoice);
    }

    @Override
    public Page<InvoiceResponse> getAllInvoices(Pageable pageable) {
        UUID merchantId = securityUtils.getCurrentUserMerchantId();
        if (merchantId != null) {
            return invoiceRepository.findByDistributorMerchantId(merchantId, pageable)
                    .map(InvoiceResponse::fromEntity);
        }
        UUID distributorId = securityUtils.getDistributorIdForFiltering();
        if (distributorId != null) {
            return invoiceRepository.findByDistributorId(distributorId, pageable)
                    .map(InvoiceResponse::fromEntity);
        }
        return invoiceRepository.findAll(pageable).map(InvoiceResponse::fromEntity);
    }

    @Override
    public Page<InvoiceResponse> getInvoicesByDistributor(UUID distributorId, Pageable pageable) {
        return invoiceRepository.findByDistributorId(distributorId, pageable)
                .map(InvoiceResponse::fromEntity);
    }

    @Override
    public Page<InvoiceResponse> getInvoicesByMerchant(UUID merchantId, Pageable pageable) {
        return invoiceRepository.findByMerchantId(merchantId, pageable)
                .map(InvoiceResponse::fromEntity);
    }

    @Override
    public Page<InvoiceResponse> getInvoicesByFilters(
            UUID distributorId,
            InvoiceStatus status,
            UUID merchantId,
            LocalDate startDate,
            LocalDate endDate,
            Pageable pageable) {

        UUID effectiveDistributorId = distributorId;
        if (effectiveDistributorId == null) {
            effectiveDistributorId = securityUtils.getDistributorIdForFiltering();
        }

        // Use unsorted pageable — ORDER BY is embedded in the native query
        Pageable unsorted = org.springframework.data.domain.PageRequest.of(
                pageable.getPageNumber(), pageable.getPageSize());
        return invoiceRepository.findByFilters(
                effectiveDistributorId,
                status != null ? status.name() : null,
                merchantId,
                startDate,
                endDate,
                unsorted
        ).map(InvoiceResponse::fromEntity);
    }

    @Override
    public Page<InvoiceResponse> searchInvoices(UUID distributorId, String search, Pageable pageable) {
        UUID effectiveDistributorId = distributorId;
        if (effectiveDistributorId == null) {
            UUID merchantId = securityUtils.getCurrentUserMerchantId();
            if (merchantId != null) {
                return invoiceRepository.searchInvoicesByMerchant(merchantId, search, pageable)
                        .map(InvoiceResponse::fromEntity);
            }
            effectiveDistributorId = securityUtils.getDistributorIdForFiltering();
        }

        return invoiceRepository.searchInvoices(effectiveDistributorId, search, pageable)
                .map(InvoiceResponse::fromEntity);
    }

    @Override
    @Transactional
    public InvoiceResponse sendInvoice(UUID invoiceId, String email) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", "id", invoiceId));

        // Determine recipient email
        String recipientEmail = email;
        if (recipientEmail == null || recipientEmail.isEmpty()) {
            recipientEmail = invoice.getMerchant().getEmail();
        }

        if (recipientEmail == null || recipientEmail.isEmpty()) {
            throw new ValidationException("No email address available for sending invoice");
        }

        // Send the invoice
        sendInvoiceEmailAsync(invoice, recipientEmail);

        // Mark as sent
        invoice.markAsSent(recipientEmail);
        invoice = invoiceRepository.save(invoice);

        log.info("Invoice {} sent to {}", invoice.getInvoiceNumber(), recipientEmail);

        return InvoiceResponse.fromEntity(invoice);
    }

    @Override
    @Transactional
    public InvoiceResponse markAsViewed(UUID invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", "id", invoiceId));

        invoice.markAsViewed();
        invoice = invoiceRepository.save(invoice);

        return InvoiceResponse.fromEntity(invoice);
    }

    @Override
    @Transactional
    public InvoiceResponse recordPayment(UUID invoiceId, BigDecimal amount, Long paymentMethodId, String externalReference) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", "id", invoiceId));

        if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
            throw new ValidationException("Cannot record payment for cancelled invoice");
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Payment amount must be positive");
        }

        InvoiceStatus previousStatus = invoice.getStatus();
        invoice.recordPayment(amount);
        invoice = invoiceRepository.save(invoice);

        log.info("Payment of {} recorded for invoice {}", amount, invoice.getInvoiceNumber());

        // Auto-post to GL: DR Cash & Bank / CR Accounts Receivable
        try {
            glAutoPostingService.postPaymentReceived(invoice, amount);
        } catch (Exception e) {
            log.warn("GL auto-post skipped (payment received) for {}: {}", invoice.getInvoiceNumber(), e.getMessage());
        }

        // Create a completed Payment record so transactions appear on the payments list
        if (invoice.getDistributor() != null && invoice.getMerchant() != null) {
            PaymentRequest paymentRequest = PaymentRequest.builder()
                    .invoiceId(invoice.getId())
                    .orderId(invoice.getOrder() != null ? invoice.getOrder().getId() : null)
                    .merchantId(invoice.getMerchant().getId())
                    .distributorId(invoice.getDistributor().getId())
                    .paymentMethodId(paymentMethodId)
                    .amount(amount)
                    .currency("KES")
                    .externalReference(externalReference)
                    .notes("Payment for " + invoice.getInvoiceNumber())
                    .build();
            var created = paymentService.createPayment(paymentRequest);
            // Payment via invoice is already received — mark completed immediately
            paymentService.updatePaymentStatus(created.getId(), PaymentStatus.COMPLETED);
        }

        // When a POS invoice is fully paid, mark the linked sale as COMPLETED
        if (previousStatus != InvoiceStatus.PAID
                && invoice.getStatus() == InvoiceStatus.PAID
                && invoice.getPosOrder() != null) {
            PosSale sale = posSaleRepository.findById(invoice.getPosOrder().getId()).orElse(null);
            if (sale != null && sale.getStatus() == PosSaleStatus.UNPAID) {
                sale.setStatus(PosSaleStatus.COMPLETED);
                sale.setAmountPaid(invoice.getPaidAmount());
                sale.setCompletedAt(LocalDateTime.now());
                if (sale.getReceiptNumber() == null) {
                    sale.setReceiptNumber("RCP-"
                            + DateTimeFormatter.ofPattern("yyyyMMdd").format(LocalDateTime.now())
                            + "-" + String.format("%05d", Math.abs(sale.getId().hashCode() % 100000)));
                }
                posSaleRepository.save(sale);
                log.info("POS sale {} marked COMPLETED after invoice {} was fully paid",
                        sale.getId(), invoice.getInvoiceNumber());
            }
        }

        return InvoiceResponse.fromEntity(invoice);
    }

    @Override
    @Transactional
    public InvoiceResponse cancelInvoice(UUID invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", "id", invoiceId));

        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new ValidationException("Cannot cancel a paid invoice");
        }

        invoice.setStatus(InvoiceStatus.CANCELLED);
        invoice = invoiceRepository.save(invoice);

        log.info("Invoice {} cancelled", invoice.getInvoiceNumber());

        return InvoiceResponse.fromEntity(invoice);
    }

    @Override
    public List<InvoiceResponse> getOverdueInvoices() {
        return invoiceRepository.findOverdueInvoices(LocalDate.now())
                .stream()
                .map(InvoiceResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional
    public void updateOverdueStatuses() {
        List<Invoice> overdueInvoices = invoiceRepository.findOverdueInvoices(LocalDate.now());
        for (Invoice invoice : overdueInvoices) {
            if (invoice.isOverdue() && invoice.getStatus() != InvoiceStatus.OVERDUE) {
                invoice.setStatus(InvoiceStatus.OVERDUE);
                invoiceRepository.save(invoice);
                log.info("Invoice {} marked as overdue", invoice.getInvoiceNumber());
            }
        }
    }

    @Override
    public long getInvoiceCountByStatus(UUID distributorId, InvoiceStatus status) {
        UUID effectiveDistributorId = distributorId;
        if (effectiveDistributorId == null) {
            UUID merchantId = securityUtils.getCurrentUserMerchantId();
            if (merchantId != null) {
                return invoiceRepository.countByDistributorMerchantIdAndStatus(merchantId, status);
            }
            effectiveDistributorId = securityUtils.getDistributorIdForFiltering();
        }

        if (effectiveDistributorId != null) {
            return invoiceRepository.countByDistributorIdAndStatus(effectiveDistributorId, status);
        }
        return invoiceRepository.countByStatus(status);
    }

    @Override
    public Map<String, Long> getAllStatusCounts(UUID distributorId) {
        UUID merchantId = null;
        UUID effectiveDistributorId = distributorId;
        if (effectiveDistributorId == null) {
            merchantId = securityUtils.getCurrentUserMerchantId();
            if (merchantId == null) {
                effectiveDistributorId = securityUtils.getDistributorIdForFiltering();
            }
        }

        final UUID finalDistributorId = effectiveDistributorId;
        final UUID finalMerchantId = merchantId;
        Map<String, Long> counts = new HashMap<>();

        for (InvoiceStatus status : InvoiceStatus.values()) {
            long count;
            if (finalMerchantId != null) {
                count = invoiceRepository.countByDistributorMerchantIdAndStatus(finalMerchantId, status);
            } else if (finalDistributorId != null) {
                count = invoiceRepository.countByDistributorIdAndStatus(finalDistributorId, status);
            } else {
                count = invoiceRepository.countByStatus(status);
            }
            counts.put(status.name(), count);
        }

        long total;
        if (finalMerchantId != null) {
            total = invoiceRepository.countByDistributorMerchantId(finalMerchantId);
        } else if (finalDistributorId != null) {
            total = invoiceRepository.countByDistributorId(finalDistributorId);
        } else {
            total = invoiceRepository.count();
        }
        counts.put("ALL", total);

        return counts;
    }

    // Helper methods

    private void deductStockForOrderInvoice(Invoice invoice) {
        Order order = invoice.getOrder();
        if (order == null || order.getWarehouse() == null || order.getItems() == null) {
            log.warn("Cannot deduct stock for invoice {}: missing order, warehouse, or items",
                    invoice.getInvoiceNumber());
            return;
        }

        for (OrderItem item : order.getItems()) {
            Stock stock = stockRepository
                    .findByWarehouseIdAndProductId(order.getWarehouse().getId(), item.getProduct().getId())
                    .orElse(null);

            if (stock == null) {
                log.warn("No stock entry for product '{}' in warehouse {} — creating with negative quantity",
                        item.getProduct().getName(), order.getWarehouse().getId());
                stock = Stock.builder()
                        .warehouse(order.getWarehouse())
                        .product(item.getProduct())
                        .quantity(BigDecimal.ZERO)
                        .reservedQuantity(BigDecimal.ZERO)
                        .build();
            }

            stock.setQuantity(stock.getQuantity().subtract(item.getQuantity()));
            stockRepository.save(stock);
            log.info("Deducted {} units of '{}' for invoice {} (order {})",
                    item.getQuantity(), item.getProduct().getName(),
                    invoice.getInvoiceNumber(), order.getOrderNumber());
        }
    }

    private String generateInvoiceNumber(UUID distributorId, String distributorName) {
        String prefix = initials(distributorName) + "-INV-";
        Integer maxNum = invoiceRepository.findMaxInvoiceNumberByDistributorAndPrefix(distributorId, prefix);
        int nextNum = (maxNum != null ? maxNum : 0) + 1;
        return prefix + String.format("%02d", nextNum);
    }

    /** Extracts uppercase initials — e.g. "Menace Distributor" → "MD". */
    static String initials(String name) {
        if (name == null || name.isBlank()) return "ORG";
        String[] words = name.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isBlank()) sb.append(Character.toUpperCase(w.charAt(0)));
        }
        String result = sb.toString();
        if (result.length() == 1 && words[0].length() >= 2) {
            result = words[0].substring(0, 2).toUpperCase();
        }
        return result.isEmpty() ? "ORG" : result;
    }

    private void sendPosReceiptEmail(Invoice invoice, PosSale sale, boolean isPaid) {
        try {
            Map<String, Object> variables = new HashMap<>();

            variables.put("invoiceNumber", invoice.getInvoiceNumber());
            variables.put("isPaid", isPaid);
            variables.put("issueDate", invoice.getIssueDate().format(DateTimeFormatter.ofPattern("MMMM d, yyyy")));
            variables.put("dueDate", invoice.getDueDate().format(DateTimeFormatter.ofPattern("MMMM d, yyyy")));
            variables.put("subtotal", invoice.getSubtotal());
            variables.put("discountAmount", invoice.getDiscountAmount());
            variables.put("taxAmount", invoice.getTaxAmount());
            variables.put("totalAmount", invoice.getTotalAmount());
            variables.put("balanceDue", invoice.getBalanceDue());
            variables.put("orderNumber", sale.getReceiptNumber() != null ? sale.getReceiptNumber() : invoice.getInvoiceNumber());
            variables.put("notes", sale.getNotes());
            variables.put("companyName", emailConfig.getFromName());

            if (invoice.getDistributor() != null) {
                variables.put("distributorName", invoice.getDistributor().getName());
                variables.put("distributorAddress", invoice.getDistributor().getAddress());
                variables.put("distributorPhone", invoice.getDistributor().getPhone());
                variables.put("distributorEmail", invoice.getDistributor().getEmail());
            }

            com.zuqi.domain.customer.Customer customer = sale.getCustomer();
            variables.put("merchantName", customer.getBusinessName());
            variables.put("merchantOwner", customer.getOwnerName());
            variables.put("merchantAddress", customer.getAddress());
            variables.put("merchantPhone", customer.getPhone());
            variables.put("merchantEmail", customer.getEmail());

            List<Map<String, Object>> items = sale.getItems().stream()
                    .map(i -> {
                        Map<String, Object> m = new HashMap<>();
                        m.put("productName", i.getProductName());
                        m.put("quantity", i.getQuantity());
                        m.put("unitPrice", i.getUnitPrice());
                        m.put("totalAmount", i.getLineTotal());
                        return m;
                    })
                    .collect(Collectors.toList());
            variables.put("items", items);

            String subject = (isPaid ? "Payment Confirmed — " : "Receipt ") + invoice.getInvoiceNumber() + " from " +
                    (invoice.getDistributor() != null ? invoice.getDistributor().getName() : emailConfig.getFromName());

            emailService.sendInvoiceEmailAsync(customer.getEmail(), subject, variables);
            log.info("Sent POS {} email to {} for sale {}", isPaid ? "PAID receipt" : "receipt",
                    customer.getEmail(), sale.getId());
        } catch (Exception e) {
            log.warn("Failed to send POS receipt email for sale {}: {}", sale.getId(), e.getMessage());
        }
    }

    private void sendInvoiceEmailAsync(Invoice invoice, String email) {
        Map<String, Object> variables = new HashMap<>();

        // Invoice details
        variables.put("invoiceNumber", invoice.getInvoiceNumber());
        variables.put("issueDate", invoice.getIssueDate().format(DateTimeFormatter.ofPattern("MMMM d, yyyy")));
        variables.put("dueDate", invoice.getDueDate().format(DateTimeFormatter.ofPattern("MMMM d, yyyy")));

        // Amounts
        variables.put("subtotal", invoice.getSubtotal());
        variables.put("discountAmount", invoice.getDiscountAmount());
        variables.put("taxAmount", invoice.getTaxAmount());
        variables.put("totalAmount", invoice.getTotalAmount());
        variables.put("balanceDue", invoice.getBalanceDue());

        // Distributor (sender) details
        if (invoice.getDistributor() != null) {
            variables.put("distributorName", invoice.getDistributor().getName());
            variables.put("distributorAddress", invoice.getDistributor().getAddress());
            variables.put("distributorPhone", invoice.getDistributor().getPhone());
            variables.put("distributorEmail", invoice.getDistributor().getEmail());
        }

        // Merchant (recipient) details
        if (invoice.getMerchant() != null) {
            variables.put("merchantName", invoice.getMerchant().getBusinessName());
            variables.put("merchantOwner", invoice.getMerchant().getOwnerName());
            variables.put("merchantAddress", invoice.getMerchant().getAddress());
            variables.put("merchantPhone", invoice.getMerchant().getPhone());
            variables.put("merchantEmail", invoice.getMerchant().getEmail());
        }

        // Payment status flag for template badge
        variables.put("isPaid", invoice.getStatus() != null && invoice.getStatus().name().equals("PAID"));

        // Items — order, POS, or manual invoice line items
        if (invoice.getOrder() != null && invoice.getOrder().getItems() != null) {
            variables.put("orderNumber", invoice.getOrder().getOrderNumber());
            List<Map<String, Object>> items = invoice.getOrder().getItems().stream()
                    .map(i -> {
                        Map<String, Object> m = new HashMap<>();
                        m.put("productName", i.getProduct() != null ? i.getProduct().getName() : "");
                        m.put("quantity", i.getQuantity());
                        m.put("unitPrice", i.getUnitPrice());
                        m.put("discountPercent", i.getDiscountPercent());
                        m.put("totalAmount", i.getTotalAmount());
                        return m;
                    })
                    .collect(Collectors.toList());
            variables.put("items", items);
        } else if (invoice.getInvoiceItems() != null && !invoice.getInvoiceItems().isEmpty()) {
            List<Map<String, Object>> items = invoice.getInvoiceItems().stream()
                    .map(i -> {
                        Map<String, Object> m = new HashMap<>();
                        m.put("productName", i.getDescription());
                        m.put("quantity", java.math.BigDecimal.valueOf(i.getQuantity()));
                        m.put("unitPrice", i.getUnitPrice());
                        m.put("discountPercent", i.getDiscountPercent());
                        m.put("totalAmount", i.getTotalAmount());
                        return m;
                    })
                    .collect(Collectors.toList());
            variables.put("items", items);
        }

        variables.put("companyName", emailConfig.getFromName());
        variables.put("notes", invoice.getNotes());
        variables.put("payUrl", frontendUrl + "/invoice/view/" + invoice.getInvoiceNumber());

        String subject = "Invoice " + invoice.getInvoiceNumber() + " from " +
                (invoice.getDistributor() != null ? invoice.getDistributor().getName() : emailConfig.getFromName());

        emailService.sendInvoiceEmailAsync(email, subject, variables);
    }
}
