package com.zuqi.domain.credit;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity for tracking real merchant credit outcomes (defaults, successes).
 *
 * Records actual outcomes when merchants:
 * - Default on payment (30+ days overdue)
 * - Complete credit term successfully
 * - Experience fraud, business closure, etc. (manual override)
 *
 * Used for:
 * - Retraining ML models with real data (not just synthetic)
 * - Calculating model accuracy (predicted vs actual default rate)
 * - Identifying high-risk merchant segments
 *
 * Table: merchant_credit_outcomes
 * Blueprint: ML_IMPLEMENTATION_PLAN.md - Phase 3, Task 8
 */
@Entity
@Table(name = "merchant_credit_outcomes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MerchantCreditOutcome {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Merchant who experienced the outcome.
     */
    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    /**
     * Original credit application (if available).
     * May be null for older merchants before credit system was deployed.
     */
    @Column(name = "credit_application_id")
    private UUID creditApplicationId;

    /**
     * Outcome: "DEFAULT" or "NO_DEFAULT".
     */
    @Column(nullable = false, length = 20)
    private String outcome;

    /**
     * Date when the outcome occurred.
     */
    @Column(name = "outcome_date", nullable = false)
    private LocalDateTime outcomeDate;

    /**
     * Reason for the outcome (for audit trail).
     * Examples:
     * - "30+ days overdue"
     * - "All payments on time"
     * - "Fraud detected"
     * - "Business closed"
     */
    @Column(length = 500)
    private String reason;

    /**
     * User who recorded the outcome (admin ID for manual entries, null for system).
     */
    @Column(name = "recorded_by")
    private UUID recordedBy;

    /**
     * Whether this outcome has been used for model retraining.
     * False = new outcome, not yet used.
     * True = already used in training pipeline.
     */
    @Column(name = "used_for_training", nullable = false)
    @Builder.Default
    private Boolean usedForTraining = false;

    /**
     * When the record was created.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (usedForTraining == null) {
            usedForTraining = false;
        }
    }
}
