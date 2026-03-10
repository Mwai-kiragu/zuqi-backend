package com.zuqi.service.impl;

import com.zuqi.api.dto.invoice.InvoiceResponse;
import com.zuqi.config.EmailConfig;
import com.zuqi.domain.invoice.Invoice;
import com.zuqi.domain.invoice.InvoiceStatus;
import com.zuqi.domain.order.Order;
import com.zuqi.domain.order.OrderItem;
import com.zuqi.domain.pos.PosSale;
import com.zuqi.domain.pos.PosSaleStatus;
import com.zuqi.domain.inventory.Stock;
import com.zuqi.exception.ResourceNotFoundException;
import com.zuqi.exception.ValidationException;
import com.zuqi.api.dto.payment.PaymentRequest;
import com.zuqi.domain.payment.PaymentStatus;
import com.zuqi.repository.InvoiceRepository;
import com.zuqi.repository.PosSaleRepository;
import com.zuqi.repository.StockRepository;
import com.zuqi.service.EmailService;
import com.zuqi.service.GlAutoPostingService;
import com.zuqi.service.InvoiceService;
import com.zuqi.service.PaymentService;
import com.zuqi.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class InvoiceServiceImpl implements InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final PosSaleRepository posSaleRepository;
    private final StockRepository stockRepository;
    private final EmailService emailService;
    private final EmailConfig emailConfig;
    private final SecurityUtils securityUtils;
    private final PaymentService paymentService;
    private final GlAutoPostingService glAutoPostingService;

    @Override
    @Transactional
    public InvoiceResponse createInvoiceFromOrder(Order order) {
        log.info("Creating invoice for order: {}", order.getOrderNumber());

        // Check if invoice already exists for this order
        if (invoiceRepository.findByOrderId(order.getId()).isPresent()) {
            throw new ValidationException("Invoice already exists for order: " + order.getOrderNumber());
        }

        // Generate invoice number
        String invoiceNumber = generateInvoiceNumber();

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
        glAutoPostingService.postInvoiceCreated(invoice);

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

        boolean isCompleted = sale.getStatus() == PosSaleStatus.COMPLETED;
        InvoiceStatus status = isCompleted ? InvoiceStatus.PAID : InvoiceStatus.SENT;
        BigDecimal paidAmount = isCompleted ? sale.getAmountPaid() : BigDecimal.ZERO;

        // Upsert: update existing invoice if found
        Optional<Invoice> existing = invoiceRepository.findByPosOrderId(saleId);
        if (existing.isPresent()) {
            Invoice invoice = existing.get();
            invoice.setAmount(sale.getTotalAmount());
            invoice.setSubtotal(sale.getSubtotal());
            invoice.setDiscountAmount(sale.getDiscountAmount());
            invoice.setTaxAmount(sale.getTaxAmount());
            invoice.setTotalAmount(sale.getTotalAmount());
            invoice.setPaidAmount(paidAmount);
            invoice.setStatus(status);
            invoice.calculateBalanceDue();
            log.info("POS invoice updated: {}", invoice.getInvoiceNumber());
            return InvoiceResponse.fromEntity(invoiceRepository.save(invoice));
        }

        // Create new invoice
        String invoiceNumber = generateInvoiceNumber();
        Invoice invoice = Invoice.builder()
                .invoiceNumber(invoiceNumber)
                .sourceType("POS_SALE")
                .posOrder(sale)
                .distributor(sale.getBranch().getDistributor())
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

        return invoiceRepository.findByFilters(
                effectiveDistributorId,
                status != null ? status.name() : null,
                merchantId,
                startDate,
                endDate,
                pageable
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
        glAutoPostingService.postPaymentReceived(invoice, amount);

        // Create a completed Payment record so transactions appear on the payments list
        if (invoice.getDistributor() != null && invoice.getMerchant() != null) {
            PaymentRequest paymentRequest = PaymentRequest.builder()
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
            if (sale != null && sale.getStatus() == PosSaleStatus.DRAFT) {
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

    private String generateInvoiceNumber() {
        String prefix = "INV-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-";
        Integer maxNum = invoiceRepository.findMaxInvoiceNumberByPrefix(prefix);
        int nextNum = (maxNum != null ? maxNum : 0) + 1;
        return prefix + String.format("%04d", nextNum);
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

        // Order items
        if (invoice.getOrder() != null && invoice.getOrder().getItems() != null) {
            variables.put("orderNumber", invoice.getOrder().getOrderNumber());
            variables.put("items", invoice.getOrder().getItems());
        }

        variables.put("companyName", emailConfig.getFromName());
        variables.put("notes", invoice.getNotes());

        String subject = "Invoice " + invoice.getInvoiceNumber() + " from " +
                (invoice.getDistributor() != null ? invoice.getDistributor().getName() : emailConfig.getFromName());

        emailService.sendInvoiceEmailAsync(email, subject, variables);
    }
}
