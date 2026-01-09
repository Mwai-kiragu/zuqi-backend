package com.zuqi.service.impl;

import com.zuqi.api.dto.invoice.InvoiceResponse;
import com.zuqi.config.EmailConfig;
import com.zuqi.domain.invoice.Invoice;
import com.zuqi.domain.invoice.InvoiceStatus;
import com.zuqi.domain.order.Order;
import com.zuqi.exception.ResourceNotFoundException;
import com.zuqi.exception.ValidationException;
import com.zuqi.repository.InvoiceRepository;
import com.zuqi.service.EmailService;
import com.zuqi.service.InvoiceService;
import com.zuqi.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class InvoiceServiceImpl implements InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final EmailService emailService;
    private final EmailConfig emailConfig;
    private final SecurityUtils securityUtils;

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
                status,
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
    public InvoiceResponse recordPayment(UUID invoiceId, BigDecimal amount) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", "id", invoiceId));

        if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
            throw new ValidationException("Cannot record payment for cancelled invoice");
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Payment amount must be positive");
        }

        invoice.recordPayment(amount);
        invoice = invoiceRepository.save(invoice);

        log.info("Payment of {} recorded for invoice {}", amount, invoice.getInvoiceNumber());

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
            effectiveDistributorId = securityUtils.getDistributorIdForFiltering();
        }

        if (effectiveDistributorId != null) {
            return invoiceRepository.countByDistributorIdAndStatus(effectiveDistributorId, status);
        }
        return invoiceRepository.countByStatus(status);
    }

    @Override
    public Map<String, Long> getAllStatusCounts(UUID distributorId) {
        UUID effectiveDistributorId = distributorId;
        if (effectiveDistributorId == null) {
            effectiveDistributorId = securityUtils.getDistributorIdForFiltering();
        }

        Map<String, Long> counts = new HashMap<>();

        // Get counts for all statuses
        for (InvoiceStatus status : InvoiceStatus.values()) {
            long count;
            if (effectiveDistributorId != null) {
                count = invoiceRepository.countByDistributorIdAndStatus(effectiveDistributorId, status);
            } else {
                count = invoiceRepository.countByStatus(status);
            }
            counts.put(status.name(), count);
        }

        // Add total count
        long total;
        if (effectiveDistributorId != null) {
            total = invoiceRepository.countByDistributorId(effectiveDistributorId);
        } else {
            total = invoiceRepository.count();
        }
        counts.put("ALL", total);

        return counts;
    }

    // Helper methods

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
