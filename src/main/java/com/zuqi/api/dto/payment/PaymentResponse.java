package com.zuqi.api.dto.payment;

import com.zuqi.domain.payment.Payment;
import com.zuqi.domain.payment.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for payments.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {

    private UUID id;
    private String paymentNumber;
    private UUID orderId;
    private String orderNumber;
    private UUID merchantId;
    private String merchantName;
    private UUID distributorId;
    private Long paymentMethodId;
    private String paymentMethodName;
    private BigDecimal amount;
    private String currency;
    private PaymentStatus status;
    private String externalReference;
    private String transactionId;
    private LocalDateTime paymentDate;
    private boolean reconciled;
    private LocalDateTime reconciledAt;
    private UUID reconciledById;
    private String reconciledByName;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static PaymentResponse fromEntity(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .paymentNumber(payment.getPaymentNumber())
                .orderId(payment.getOrder() != null ? payment.getOrder().getId() : null)
                .orderNumber(payment.getOrder() != null ? payment.getOrder().getOrderNumber() : null)
                .merchantId(payment.getMerchant() != null ? payment.getMerchant().getId() : null)
                .merchantName(payment.getMerchant() != null ? payment.getMerchant().getBusinessName() : null)
                .distributorId(payment.getDistributor() != null ? payment.getDistributor().getId() : null)
                .paymentMethodId(payment.getPaymentMethod() != null ? payment.getPaymentMethod().getId() : null)
                .paymentMethodName(payment.getPaymentMethod() != null ? payment.getPaymentMethod().getName() : null)
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .status(payment.getStatus())
                .externalReference(payment.getExternalReference())
                .transactionId(payment.getTransactionId())
                .paymentDate(payment.getPaymentDate())
                .reconciled(payment.isReconciled())
                .reconciledAt(payment.getReconciledAt())
                .reconciledById(payment.getReconciledBy() != null ? payment.getReconciledBy().getId() : null)
                .reconciledByName(payment.getReconciledBy() != null ? payment.getReconciledBy().getFullName() : null)
                .notes(payment.getNotes())
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .build();
    }
}
