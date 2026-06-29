package com.zuqi.service.impl;

import com.zuqi.api.dto.payment.*;
import com.zuqi.domain.distributor.Distributor;
import com.zuqi.domain.customer.Customer;
import com.zuqi.domain.invoice.Invoice;
import com.zuqi.domain.order.Order;
import com.zuqi.domain.payment.Payment;
import com.zuqi.domain.payment.PaymentMethod;
import com.zuqi.domain.payment.PaymentStatus;
import com.zuqi.domain.pos.PosSale;
import com.zuqi.domain.pos.PosSalePayment;
import com.zuqi.domain.user.User;
import com.zuqi.exception.ResourceNotFoundException;
import com.zuqi.repository.*;
import com.zuqi.domain.mpesa.MpesaConfigStatus;
import com.zuqi.domain.kcb.KcbConfigStatus;
import com.zuqi.ai.event.PaymentRecordedEvent;
import com.zuqi.ai.feature.FeatureStore;
import com.zuqi.domain.audit.ActivityAction;
import com.zuqi.domain.user.User;
import com.zuqi.service.ActivityLogService;
import com.zuqi.service.PaymentService;
import com.zuqi.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final OrderRepository orderRepository;
    private final InvoiceRepository invoiceRepository;
    private final CustomerRepository customerRepository;
    private final DistributorRepository distributorRepository;
    private final MerchantRepository merchantRepository;
    private final MpesaConfigRepository mpesaConfigRepository;
    private final KcbConfigRepository kcbConfigRepository;
    private final SecurityUtils securityUtils;
    private final ApplicationEventPublisher eventPublisher;
    private final FeatureStore featureStore;
    private final ActivityLogService activityLogService;

    @Override
    public Page<PaymentResponse> getAllPayments(Pageable pageable) {
        UUID merchantId = securityUtils.getCurrentUserMerchantId();
        if (merchantId != null) {
            return paymentRepository.findByDistributorMerchantId(merchantId, pageable)
                    .map(PaymentResponse::fromEntity);
        }
        UUID distributorId = securityUtils.getDistributorIdForFiltering();
        if (distributorId != null) {
            return paymentRepository.findByDistributorId(distributorId, pageable)
                    .map(PaymentResponse::fromEntity);
        }
        return paymentRepository.findAll(pageable).map(PaymentResponse::fromEntity);
    }

    @Override
    public Page<PaymentResponse> getPaymentsByDistributor(UUID distributorId, Pageable pageable) {
        return paymentRepository.findByDistributorId(distributorId, pageable)
                .map(PaymentResponse::fromEntity);
    }

    @Override
    public Page<PaymentResponse> getPaymentsByMerchant(UUID merchantId, Pageable pageable) {
        return paymentRepository.findByMerchantId(merchantId, pageable)
                .map(PaymentResponse::fromEntity);
    }

    @Override
    public Page<PaymentResponse> getPaymentsByOrder(UUID orderId, Pageable pageable) {
        return paymentRepository.findByOrderId(orderId, pageable)
                .map(PaymentResponse::fromEntity);
    }

    @Override
    public Page<PaymentResponse> getPaymentsByFilters(
            UUID distributorId,
            PaymentStatus status,
            UUID merchantId,
            Boolean reconciled,
            Long paymentMethodId,
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

        // Strip sort from pageable — native query has explicit ORDER BY p.created_at DESC
        Pageable unsorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        return paymentRepository.findByFilters(
                effectiveDistributorId,
                status != null ? status.name() : null,
                merchantId,
                reconciled,
                paymentMethodId,
                startDateTime,
                endDateTime,
                unsorted
        ).map(PaymentResponse::fromEntity);
    }

    @Override
    public Page<PaymentResponse> searchPayments(UUID distributorId, String search, Pageable pageable) {
        UUID effectiveDistributorId = distributorId;
        if (effectiveDistributorId == null) {
            UUID merchantId = securityUtils.getCurrentUserMerchantId();
            if (merchantId != null) {
                return paymentRepository.searchPaymentsByMerchant(merchantId, search, pageable)
                        .map(PaymentResponse::fromEntity);
            }
            effectiveDistributorId = securityUtils.getDistributorIdForFiltering();
        }

        return paymentRepository.searchPayments(effectiveDistributorId, search, pageable)
                .map(PaymentResponse::fromEntity);
    }

    @Override
    public PaymentResponse getPaymentById(UUID id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", "id", id));
        return PaymentResponse.fromEntity(payment);
    }

    @Override
    public PaymentResponse getPaymentByNumber(String paymentNumber) {
        Payment payment = paymentRepository.findByPaymentNumber(paymentNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", "paymentNumber", paymentNumber));
        return PaymentResponse.fromEntity(payment);
    }

    @Override
    @Transactional
    public PaymentResponse createPayment(PaymentRequest request) {
        log.info("Creating payment for merchant: {}", request.getMerchantId());

        // Validate and fetch related entities
        Distributor distributor = distributorRepository.findById(request.getDistributorId())
                .orElseThrow(() -> new ResourceNotFoundException("Distributor", "id", request.getDistributorId()));

        Customer merchant = null;
        if (request.getMerchantId() != null) {
            merchant = customerRepository.findById(request.getMerchantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", request.getMerchantId()));
        }

        Order order = null;
        if (request.getOrderId() != null) {
            order = orderRepository.findById(request.getOrderId())
                    .orElseThrow(() -> new ResourceNotFoundException("Order", "id", request.getOrderId()));
        }

        Invoice invoice = null;
        if (request.getInvoiceId() != null) {
            invoice = invoiceRepository.findById(request.getInvoiceId()).orElse(null);
        }

        PaymentMethod paymentMethod = null;
        if (request.getPaymentMethodId() != null) {
            paymentMethod = paymentMethodRepository.findById(request.getPaymentMethodId())
                    .orElseThrow(() -> new ResourceNotFoundException("PaymentMethod", "id", request.getPaymentMethodId()));
        }

        // Generate payment number
        String paymentNumber = generatePaymentNumber();

        // Auto-generate a cash reference for cash payments that have no external reference
        String externalReference = request.getExternalReference();
        if (externalReference == null && paymentMethod != null
                && "CASH".equalsIgnoreCase(paymentMethod.getCode())) {
            externalReference = generateCashReference(distributor.getName());
        }

        // Create payment
        Payment payment = Payment.builder()
                .paymentNumber(paymentNumber)
                .sourceType(order != null ? "ORDER" : invoice != null ? "INVOICE" : "MANUAL")
                .order(order)
                .invoice(invoice)
                .merchant(merchant)
                .distributor(distributor)
                .paymentMethod(paymentMethod)
                .amount(request.getAmount())
                .currency(request.getCurrency() != null ? request.getCurrency() : "KES")
                .status(PaymentStatus.PENDING)
                .externalReference(externalReference)
                .notes(request.getNotes())
                .build();

        payment = paymentRepository.save(payment);

        // Update order paid amount if linked to an order
        if (order != null) {
            updateOrderPaidAmount(order);
        }

        // Invalidate payment feature cache for this merchant (affects anomaly detection)
        featureStore.invalidateMerchantCache(payment.getMerchant().getId());
        log.debug("Invalidated feature cache for merchant {} after payment recording", payment.getMerchant().getId());

        // Publish AI event for payment anomaly detection
        publishPaymentRecordedEvent(payment);

        log.info("Payment created successfully: {}", payment.getPaymentNumber());

        User currentUser = securityUtils.getCurrentUser();
        if (currentUser != null) {
            activityLogService.log(
                currentUser.getId(), currentUser.getEmail(),
                currentUser.getFirstName() + " " + currentUser.getLastName(),
                ActivityAction.CREATE, "PAYMENT", payment.getId(),
                payment.getPaymentNumber(), "PAYMENTS", "Recorded payment: " + payment.getPaymentNumber() + " — " + payment.getAmount()
            );
        }

        return PaymentResponse.fromEntity(payment);
    }

    @Override
    @Transactional
    public PaymentResponse updatePaymentStatus(UUID id, PaymentStatus status) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", "id", id));

        payment.setStatus(status);

        if (status == PaymentStatus.COMPLETED) {
            payment.setPaymentDate(LocalDateTime.now());

            // Update order paid amount
            if (payment.getOrder() != null) {
                updateOrderPaidAmount(payment.getOrder());
            }
        }

        payment = paymentRepository.save(payment);
        log.info("Payment {} status updated to {}", payment.getPaymentNumber(), status);

        // Publish AI event when payment is completed
        if (status == PaymentStatus.COMPLETED) {
            publishPaymentRecordedEvent(payment);
        }

        return PaymentResponse.fromEntity(payment);
    }

    @Override
    @Transactional
    public PaymentResponse reconcilePayment(UUID id, ReconcileRequest request, User currentUser) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", "id", id));

        payment.setReconciled(true);
        payment.setReconciledAt(LocalDateTime.now());
        payment.setReconciledBy(currentUser);

        if (request.getNotes() != null) {
            payment.setNotes(request.getNotes());
        }

        payment = paymentRepository.save(payment);
        log.info("Payment {} reconciled by {}", payment.getPaymentNumber(), currentUser.getEmail());

        return PaymentResponse.fromEntity(payment);
    }

    @Override
    public List<PaymentResponse> getUnreconciledPayments(UUID distributorId) {
        return paymentRepository.findUnreconciledPayments(distributorId)
                .stream()
                .map(PaymentResponse::fromEntity)
                .toList();
    }

    @Override
    public long countUnreconciledPayments(UUID distributorId) {
        return paymentRepository.countUnreconciledPayments(distributorId);
    }

    @Override
    public List<PaymentMethodResponse> getAllPaymentMethods() {
        return paymentMethodRepository.findAll()
                .stream()
                .map(PaymentMethodResponse::fromEntity)
                .toList();
    }

    @Override
    public List<PaymentMethodResponse> getActivePaymentMethods() {
        List<PaymentMethod> dbMethods = paymentMethodRepository.findByActiveTrue();

        UUID merchantId = securityUtils.getCurrentUserMerchantId();
        if (merchantId == null) {
            // SUPER_ADMIN or no merchant context — return all as-is
            return dbMethods.stream().map(PaymentMethodResponse::fromEntity).toList();
        }

        // Check merchant-specific availability
        boolean cashEnabled = merchantRepository.findById(merchantId)
                .map(m -> m.isCashEnabled())
                .orElse(true);

        boolean mpesaEnabled = mpesaConfigRepository.findByMerchantId(merchantId)
                .stream()
                .anyMatch(c -> c.getStatus() == MpesaConfigStatus.ACTIVE);

        boolean kcbEnabled = kcbConfigRepository.existsByMerchantIdAndStatus(merchantId, KcbConfigStatus.ACTIVE);

        return dbMethods.stream().map(m -> {
            String code = m.getCode() != null ? m.getCode().toUpperCase() : "";
            boolean available = switch (code) {
                case "CASH"  -> cashEnabled;
                case "MPESA" -> mpesaEnabled;
                case "KCB"   -> kcbEnabled;
                default      -> m.isActive();
            };
            return PaymentMethodResponse.builder()
                    .id(m.getId())
                    .name(m.getName())
                    .code(m.getCode())
                    .description(m.getDescription())
                    .active(available)
                    .build();
        }).toList();
    }

    @Override
    @Transactional
    public void createPaymentsForPosSale(PosSale sale) {
        if (sale.getPayments() == null || sale.getPayments().isEmpty()) {
            return;
        }
        for (PosSalePayment posPayment : sale.getPayments()) {
            // Map PosPaymentMethod enum name → PaymentMethod entity by code
            PaymentMethod method = paymentMethodRepository
                    .findByCode(posPayment.getPaymentMethod().name())
                    .orElse(null);

            // Auto-generate a cash reference if none was provided
            String ref = posPayment.getReferenceNumber();
            if (ref == null && posPayment.getPaymentMethod() == com.zuqi.domain.pos.PosPaymentMethod.CASH) {
                ref = generateCashReference(sale.getBranch().getDistributor().getName());
                posPayment.setReferenceNumber(ref);
            }

            Payment payment = Payment.builder()
                    .paymentNumber(generatePaymentNumber())
                    .sourceType("POS_SALE")
                    .posSale(sale)
                    .distributor(sale.getBranch().getDistributor())
                    .paymentMethod(method)
                    .amount(posPayment.getAmount())
                    .currency("KES")
                    .status(PaymentStatus.COMPLETED)
                    .paymentDate(sale.getCompletedAt())
                    .externalReference(ref)
                    .notes(posPayment.getNotes())
                    .build();

            paymentRepository.save(payment);
        }
        log.info("Created {} payment record(s) for POS sale {}", sale.getPayments().size(), sale.getId());
    }

    @Override
    @Transactional
    public void createPaymentForPosSalePayment(PosSale sale, com.zuqi.domain.pos.PosSalePayment posPayment) {
        PaymentMethod method = paymentMethodRepository
                .findByCode(posPayment.getPaymentMethod().name())
                .orElse(null);

        String ref = posPayment.getReferenceNumber();
        if (ref == null && posPayment.getPaymentMethod() == com.zuqi.domain.pos.PosPaymentMethod.CASH) {
            ref = generateCashReference(sale.getBranch().getDistributor().getName());
            posPayment.setReferenceNumber(ref);
        }

        Payment payment = Payment.builder()
                .paymentNumber(generatePaymentNumber())
                .sourceType("POS_SALE")
                .posSale(sale)
                .distributor(sale.getBranch().getDistributor())
                .paymentMethod(method)
                .amount(posPayment.getAmount())
                .currency("KES")
                .status(PaymentStatus.COMPLETED)
                .paymentDate(LocalDateTime.now())
                .externalReference(ref)
                .notes(posPayment.getNotes())
                .build();

        paymentRepository.save(payment);
        log.info("Created payment record for POS settle on sale {} ({})", sale.getId(), posPayment.getPaymentMethod());
    }

    // Helper methods

    private String generatePaymentNumber() {
        String prefix = "PAY-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-";
        Integer maxNum = paymentRepository.findMaxPaymentNumberByPrefix(prefix);
        int nextNum = (maxNum != null ? maxNum : 0) + 1;
        return prefix + String.format("%04d", nextNum);
    }

    private String generateCashReference(String distributorName) {
        String prefix = initials(distributorName) + "-";
        Integer maxNum = paymentRepository.findMaxCashReferenceByPrefix(prefix);
        int nextNum = (maxNum != null ? maxNum : 0) + 1;
        return prefix + String.format("%03d", nextNum);
    }

    /** Extracts uppercase initials from a name — e.g. "Menace Distributor" → "MD". */
    static String initials(String name) {
        if (name == null || name.isBlank()) return "ORG";
        String[] words = name.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isBlank()) sb.append(Character.toUpperCase(w.charAt(0)));
        }
        String result = sb.toString();
        // Single-word name: take first 2 characters instead of just 1
        if (result.length() == 1 && words[0].length() >= 2) {
            result = words[0].substring(0, 2).toUpperCase();
        }
        return result.isEmpty() ? "ORG" : result;
    }

    private void updateOrderPaidAmount(Order order) {
        BigDecimal totalPaid = paymentRepository.sumCompletedPaymentsByOrder(order.getId());
        if (totalPaid == null) {
            totalPaid = BigDecimal.ZERO;
        }
        order.setPaidAmount(totalPaid);

        // Update payment status
        if (totalPaid.compareTo(order.getTotalAmount()) >= 0) {
            order.setPaymentStatus(com.zuqi.domain.order.PaymentStatus.PAID);
        } else if (totalPaid.compareTo(BigDecimal.ZERO) > 0) {
            order.setPaymentStatus(com.zuqi.domain.order.PaymentStatus.PARTIAL);
        }

        orderRepository.save(order);
    }

    @Override
    public PaymentStatsResponse getPaymentStats(
            UUID distributorId,
            PaymentStatus status,
            UUID merchantId,
            Boolean reconciled,
            Long paymentMethodId,
            LocalDate startDate,
            LocalDate endDate) {

        LocalDateTime startDt = startDate != null ? startDate.atStartOfDay() : null;
        LocalDateTime endDt = endDate != null ? endDate.atTime(23, 59, 59) : null;

        PaymentRepository.PaymentStatsView view;
        UUID effectiveDistributorId = distributorId != null
                ? distributorId : securityUtils.getDistributorIdForFiltering();

        if (effectiveDistributorId != null) {
            view = paymentRepository.statsForDistributorByFilters(
                    effectiveDistributorId,
                    status != null ? status.name() : null,
                    merchantId, reconciled, paymentMethodId, startDt, endDt);
        } else {
            UUID mId = securityUtils.getCurrentUserMerchantId();
            if (mId != null) {
                view = paymentRepository.statsForMerchant(mId);
            } else {
                view = paymentRepository.statsAll();
            }
        }

        return PaymentStatsResponse.builder()
                .totalAmount(coalesce(view.getTotalAmount()))
                .completedAmount(coalesce(view.getCompletedAmount()))
                .pendingAmount(coalesce(view.getPendingAmount()))
                .count(view.getPaymentCount() != null ? view.getPaymentCount() : 0L)
                .build();
    }

    @Override
    public List<PaymentResponse> getAllForExport() {
        UUID merchantId = securityUtils.getCurrentUserMerchantId();
        UUID distributorId = securityUtils.getDistributorIdForFiltering();
        if (merchantId != null) {
            return paymentRepository.findByDistributorMerchantIdOrderByCreatedAtDesc(merchantId)
                    .stream().map(PaymentResponse::fromEntity).collect(java.util.stream.Collectors.toList());
        } else if (distributorId != null) {
            return paymentRepository.findByDistributorIdOrderByCreatedAtDesc(distributorId)
                    .stream().map(PaymentResponse::fromEntity).collect(java.util.stream.Collectors.toList());
        }
        return paymentRepository.findAll(org.springframework.data.domain.Sort.by("createdAt").descending())
                .stream().map(PaymentResponse::fromEntity).collect(java.util.stream.Collectors.toList());
    }

    private BigDecimal coalesce(BigDecimal val) {
        return val != null ? val : BigDecimal.ZERO;
    }

    private void publishPaymentRecordedEvent(Payment payment) {
        PaymentRecordedEvent event = new PaymentRecordedEvent(
                payment.getId(),
                payment.getOrder() != null ? payment.getOrder().getId() : null,
                payment.getMerchant().getId(),
                payment.getDistributor().getId(),
                payment.getAmount(),
                payment.getPaymentMethod() != null ? payment.getPaymentMethod().getName() : "UNKNOWN",
                payment.getCreatedAt(),
                payment.getStatus().name()
        );
        eventPublisher.publishEvent(event);
        log.debug("Published PaymentRecordedEvent for payment {}", payment.getId());
    }
}
