package com.zuqi.domain.ai;

import com.zuqi.domain.distributor.Distributor;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ai_model_registry")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AIModelRegistry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "model_name", nullable = false, length = 100)
    private String modelName;

    @Column(name = "model_version", nullable = false)
    private Integer modelVersion;

    @Column(nullable = false, length = 50)
    private String algorithm;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ModelStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "distributor_id")
    private Distributor distributor;

    @Column(name = "training_data_start")
    private LocalDateTime trainingDataStart;

    @Column(name = "training_data_end")
    private LocalDateTime trainingDataEnd;

    @Column(name = "training_record_count")
    private Integer trainingRecordCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "performance_metrics", columnDefinition = "jsonb")
    private Map<String, Object> performanceMetrics;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> hyperparameters;

    @Column(name = "model_binary", columnDefinition = "bytea")
    private byte[] modelBinary;

    @Column(name = "model_size_bytes")
    private Long modelSizeBytes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "feature_columns", columnDefinition = "jsonb")
    private Map<String, Object> featureColumns;

    // -------------------------------------------------------------------------
    // Synthetic data phase tracking (Phase 1.5)
    // -------------------------------------------------------------------------

    /** Data maturity phase at time of training: SYNTHETIC / HYBRID / REAL. */
    @Enumerated(EnumType.STRING)
    @Column(name = "data_phase", length = 20)
    private DataPhase dataPhase;

    /** Fraction of real records used in training (0.0–1.0). */
    @Column(name = "real_data_ratio")
    private Double realDataRatio;

    @Column(name = "synthetic_records_used")
    private Integer syntheticRecordsUsed;

    @Column(name = "real_records_used")
    private Integer realRecordsUsed;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "promoted_at")
    private LocalDateTime promotedAt;

    @Column(name = "retired_at")
    private LocalDateTime retiredAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
