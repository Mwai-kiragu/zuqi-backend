package com.zuqi.domain.ncba;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ncba_stk_requests", indexes = {
        @Index(name = "idx_ncba_stk_reference_id", columnList = "reference_id"),
        @Index(name = "idx_ncba_stk_status", columnList = "status"),
        @Index(name = "idx_ncba_stk_transaction_id", columnList = "transaction_id")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NcbaStkRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "reference_id", nullable = false, length = 100)
    private String referenceId;

    @Column(name = "reference_type", nullable = false, length = 30)
    private String referenceType;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "phone_number", nullable = false, length = 20)
    private String phoneNumber;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "account_no", length = 100)
    private String accountNo;

    @Column(name = "lookup_id", length = 100)
    private String lookupId;

    /** transaction_id returned by NCBA STK initiate */
    @Column(name = "transaction_id", length = 100)
    private String transactionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private NcbaStkStatus status = NcbaStkStatus.PENDING;

    @Column(name = "result_desc", length = 255)
    private String resultDesc;

    @Column(name = "callback_received_at")
    private LocalDateTime callbackReceivedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
