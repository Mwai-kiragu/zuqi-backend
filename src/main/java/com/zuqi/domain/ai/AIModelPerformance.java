package com.zuqi.domain.ai;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * JPA entity for the {@code ai_model_performance} table (migration V27).
 *
 * <p>Records a single named metric for a specific model version on a specific
 * evaluation date. The unique constraint on (model_name, model_version,
 * evaluation_date, metric_name) prevents duplicate metric entries.
 *
 * <p>Written by {@code ModelPerformanceTracker} and training pipelines after
 * each model evaluation. Read by {@code AIHealthServiceImpl} to build the
 * performance history shown on the AI health dashboard.
 *
 * <p><b>Migration:</b> V27__create_ai_model_performance.sql
 * <p><b>Blueprint:</b> plan.md Section 5.1
 */
@Entity
@Table(
        name = "ai_model_performance",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_model_performance",
                columnNames = {"model_name", "model_version", "evaluation_date", "metric_name"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AIModelPerformance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "model_name", nullable = false, length = 100)
    private String modelName;

    @Column(name = "model_version", nullable = false)
    private Integer modelVersion;

    @Column(name = "evaluation_date", nullable = false)
    private LocalDate evaluationDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "metric_name", nullable = false, length = 50)
    private MetricName metricName;

    @Column(name = "metric_value", nullable = false)
    private Double metricValue;

    @Column(name = "sample_size")
    private Integer sampleSize;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
