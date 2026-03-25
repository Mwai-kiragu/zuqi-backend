package com.zuqi.domain.pos;

import com.zuqi.domain.branch.DistributorBranch;
import com.zuqi.domain.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "pos_shifts", indexes = {
        @Index(name = "idx_pos_shifts_branch", columnList = "branch_id"),
        @Index(name = "idx_pos_shifts_cashier", columnList = "cashier_id"),
        @Index(name = "idx_pos_shifts_status", columnList = "status")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PosShift {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private DistributorBranch branch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "terminal_id")
    private PosTerminal terminal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cashier_id", nullable = false)
    private User cashier;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PosShiftStatus status = PosShiftStatus.OPEN;

    @Column(name = "opening_float", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal openingFloat = BigDecimal.ZERO;

    @Column(name = "closing_float", precision = 15, scale = 2)
    private BigDecimal closingFloat;

    @Column(name = "expected_cash", precision = 15, scale = 2)
    private BigDecimal expectedCash;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "opened_at", nullable = false)
    private LocalDateTime openedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    /** NOT_REQUIRED | PENDING | APPROVED | REJECTED */
    @Column(name = "reconciliation_status", length = 30)
    @Builder.Default
    private String reconciliationStatus = "NOT_REQUIRED";

    @Column(name = "reconciled_by_id")
    private UUID reconciledById;

    @Column(name = "reconciled_at")
    private LocalDateTime reconciledAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
