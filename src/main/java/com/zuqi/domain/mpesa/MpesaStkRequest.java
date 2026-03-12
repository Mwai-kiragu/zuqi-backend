package com.zuqi.domain.mpesa;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "mpesa_stk_requests", indexes = {
        @Index(name = "idx_mpesa_stk_checkout_id", columnList = "checkout_request_id"),
        @Index(name = "idx_mpesa_stk_reference_id", columnList = "reference_id"),
        @Index(name = "idx_mpesa_stk_status", columnList = "status")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MpesaStkRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Internal reference: orderId, saleId, invoiceId etc. */
    @Column(name = "reference_id", nullable = false, length = 100)
    private String referenceId;

    /** Type of document this payment covers */
    @Column(name = "reference_type", nullable = false, length = 30)
    private String referenceType;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "distributor_id")
    private UUID distributorId;

    @Column(name = "phone_number", nullable = false, length = 20)
    private String phoneNumber;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    /** Returned by Safaricom/external service */
    @Column(name = "checkout_request_id", length = 100)
    private String checkoutRequestId;

    @Column(name = "merchant_request_id", length = 100)
    private String merchantRequestId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private MpesaStkStatus status = MpesaStkStatus.PENDING;

    /** 0 = success, non-zero = failure */
    @Column(name = "result_code", length = 10)
    private String resultCode;

    @Column(name = "result_desc", length = 255)
    private String resultDesc;

    /** M-Pesa transaction ID from callback */
    @Column(name = "mpesa_receipt_number", length = 50)
    private String mpesaReceiptNumber;

    @Column(name = "callback_received_at")
    private LocalDateTime callbackReceivedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
