package com.zuqi.domain.ai;

import com.zuqi.domain.distributor.Distributor;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Tracks the data maturity phase (SYNTHETIC / HYBRID / REAL) for a given model,
 * optionally scoped to a specific distributor (null = global model).
 *
 * One row per (model_name, distributor_id) pair. Updated after every training run
 * by {@code DataPhaseTracker}.
 */
@Entity
@Table(
    name = "ai_data_phase",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_data_phase_model_distributor",
        columnNames = {"model_name", "distributor_id"}
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AIDataPhase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "model_name", nullable = false, length = 100)
    private String modelName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "distributor_id")
    private Distributor distributor;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_phase", nullable = false, length = 20)
    private DataPhase currentPhase;

    @Column(name = "real_data_count", nullable = false)
    @Builder.Default
    private Integer realDataCount = 0;

    @Column(name = "synthetic_data_count", nullable = false)
    @Builder.Default
    private Integer syntheticDataCount = 0;

    /** Fraction of real records vs total (0.0–1.0). */
    @Column(name = "real_data_ratio", nullable = false)
    @Builder.Default
    private Double realDataRatio = 0.0;

    @Column(name = "last_evaluated_at")
    private LocalDateTime lastEvaluatedAt;

    /** Timestamp of the most recent phase transition (e.g. SYNTHETIC → HYBRID). */
    @Column(name = "transitioned_at")
    private LocalDateTime transitionedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (currentPhase == null) {
            currentPhase = DataPhase.SYNTHETIC;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
