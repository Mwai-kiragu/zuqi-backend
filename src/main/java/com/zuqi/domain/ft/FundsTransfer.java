package com.zuqi.domain.ft;

import com.zuqi.domain.supplier.Supplier;
import com.zuqi.domain.supplier.SupplierBill;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "funds_transfers", indexes = {
        @Index(name = "idx_funds_transfers_dist", columnList = "distributor_id"),
        @Index(name = "idx_funds_transfers_stat", columnList = "distributor_id, status")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FundsTransfer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "distributor_id", nullable = false)
    private UUID distributorId;

    @Column(name = "reference_number", length = 60, unique = true)
    private String referenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "transfer_type", nullable = false, length = 20)
    @Builder.Default
    private FundsTransferType transferType = FundsTransferType.EXTERNAL;

    @Column(name = "debit_account_number", length = 100)
    private String debitAccountNumber;

    @Column(name = "debit_bank_name", length = 100)
    private String debitBankName;

    @Column(name = "credit_account_number", nullable = false, length = 100)
    private String creditAccountNumber;

    @Column(name = "credit_bank_name", length = 100)
    private String creditBankName;

    /** BANK_TRANSFER | CHEQUE | MPESA */
    @Column(name = "payment_mode", length = 20)
    private String paymentMode;

    @Column(name = "cheque_number", length = 60)
    private String chequeNumber;

    @Column(name = "cheque_date")
    private LocalDate chequeDate;

    @Column(name = "cheque_image_url", length = 500)
    private String chequeImageUrl;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(length = 10, nullable = false)
    @Builder.Default
    private String currency = "KES";

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "payment_details", columnDefinition = "TEXT")
    private String paymentDetails;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private FundsTransferStatus status = FundsTransferStatus.DRAFT;

    @Column(name = "current_approval_level", nullable = false)
    @Builder.Default
    private int currentApprovalLevel = 1;

    @Column(name = "required_approval_levels", nullable = false)
    @Builder.Default
    private int requiredApprovalLevels = 1;

    @Column(name = "amount_range_id")
    private UUID amountRangeId;

    @Column(name = "initiator_id", nullable = false)
    private UUID initiatorId;

    /** Optional link to a source document: 'EXPENSE' or 'PURCHASE_ORDER' */
    @Column(name = "reference_type", length = 30)
    private String referenceType;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "authorized_by_id")
    private UUID authorizedById;

    @Column(name = "authorized_by_name", length = 200)
    private String authorizedByName;

    @Column(name = "disbursed_at")
    private LocalDateTime disbursedAt;

    @Column(name = "rejected_reason", columnDefinition = "TEXT")
    private String rejectedReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_bill_id")
    private SupplierBill supplierBill;

    @Builder.Default
    @Column(name = "approval_status", length = 30)
    private String approvalStatus = "NOT_REQUIRED";

    /** Transaction ID returned by the payment gateway (e.g., M-Pesa ConversationID or receipt). */
    @Column(name = "gateway_transaction_id", length = 200)
    private String gatewayTransactionId;

    /** Gateway processing status: PENDING | SUCCESS | FAILED | CHEQUE_ISSUED | PENDING_BANK */
    @Column(name = "gateway_status", length = 30)
    private String gatewayStatus;

    /** Raw gateway response JSON for debugging. */
    @Column(name = "gateway_response", columnDefinition = "TEXT")
    private String gatewayResponse;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
