package com.zuqi.domain.kcb;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "kcb_stk_requests", indexes = {
        @Index(name = "idx_kcb_stk_reference_id", columnList = "reference_id"),
        @Index(name = "idx_kcb_stk_status", columnList = "status"),
        @Index(name = "idx_kcb_stk_zed_id", columnList = "zed_stk_id")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KcbStkRequest {

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

    /** The `id` field from ZED response */
    @Column(name = "zed_stk_id", length = 100)
    private String zedStkId;

    /** The `stkOrderId` from ZED response */
    @Column(name = "stk_order_id", length = 100)
    private String stkOrderId;

    /** The `requestReferenceId` from ZED response */
    @Column(name = "request_reference_id", length = 100)
    private String requestReferenceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private KcbStkStatus status = KcbStkStatus.PENDING;

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
