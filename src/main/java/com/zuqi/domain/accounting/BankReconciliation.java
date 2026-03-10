package com.zuqi.domain.accounting;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "bank_reconciliations", indexes = {
        @Index(name = "idx_bank_recon_distributor", columnList = "distributor_id"),
        @Index(name = "idx_bank_recon_date", columnList = "statement_date")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankReconciliation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "distributor_id", nullable = false)
    private UUID distributorId;

    @Column(name = "account_name", nullable = false, length = 200)
    private String accountName;

    @Column(name = "account_number", length = 50)
    private String accountNumber;

    @Column(name = "bank_name", length = 200)
    private String bankName;

    @Column(name = "statement_date", nullable = false)
    private LocalDate statementDate;

    @Column(name = "statement_balance", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal statementBalance = BigDecimal.ZERO;

    @Column(name = "system_balance", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal systemBalance = BigDecimal.ZERO;

    @Column(name = "adjusted_bank_balance", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal adjustedBankBalance = BigDecimal.ZERO;

    @Column(name = "adjusted_system_balance", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal adjustedSystemBalance = BigDecimal.ZERO;

    @Column(name = "difference", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal difference = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private BankReconciliationStatus status = BankReconciliationStatus.DRAFT;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "reconciled_by")
    private UUID reconciledBy;

    @Column(name = "reconciled_at")
    private LocalDateTime reconciledAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @OneToMany(mappedBy = "reconciliation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<BankReconciliationItem> items = new ArrayList<>();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
