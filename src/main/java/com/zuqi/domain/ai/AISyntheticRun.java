package com.zuqi.domain.ai;

import com.zuqi.domain.distributor.Distributor;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable audit record for a synthetic data generation run.
 *
 * Written once when a run starts (status=RUNNING), then updated to
 * COMPLETED or FAILED when the run finishes. Required for KCB partnership
 * compliance — every model must be traceable to its training data provenance.
 */
@Entity
@Table(name = "ai_synthetic_runs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AISyntheticRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "distributor_id")
    private Distributor distributor;

    @Enumerated(EnumType.STRING)
    @Column(name = "run_type", nullable = false, length = 30)
    private SyntheticRunType runType;

    /** Seeded RNG value — allows exact regeneration of the same dataset. */
    @Column(name = "random_seed")
    private Long randomSeed;

    @Column(name = "merchant_count")
    private Integer merchantCount;

    @Column(name = "history_months")
    private Integer historyMonths;

    /** JSON: {"STEADY_GROWER": 0.35, "STABLE_PERFORMER": 0.25, ...} */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "archetype_ratios", columnDefinition = "jsonb")
    private Map<String, Object> archetypeRatios;

    /** Full SyntheticDataConfig snapshot for exact reproducibility. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config_snapshot", columnDefinition = "jsonb")
    private Map<String, Object> configSnapshot;

    /** JSON: {"merchants": 500, "orders": 12400, "payments": 9800, ...} */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "records_generated", columnDefinition = "jsonb")
    private Map<String, Object> recordsGenerated;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private SyntheticRunStatus status = SyntheticRunStatus.RUNNING;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "triggered_by", length = 100)
    private String triggeredBy;

    @Column(name = "started_at", nullable = false, updatable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @PrePersist
    protected void onCreate() {
        if (startedAt == null) {
            startedAt = LocalDateTime.now();
        }
        if (status == null) {
            status = SyntheticRunStatus.RUNNING;
        }
    }
}
