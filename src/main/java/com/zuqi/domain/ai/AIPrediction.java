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
@Table(name = "ai_predictions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AIPrediction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "model_name", nullable = false, length = 100)
    private String modelName;

    @Column(name = "model_version", nullable = false)
    private Integer modelVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 50)
    private EntityType entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "distributor_id", nullable = false)
    private Distributor distributor;

    @Column(name = "input_features_hash", length = 64)
    private String inputFeaturesHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "prediction_value", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> predictionValue;

    @Column(name = "confidence_score")
    private Double confidenceScore;

    @Builder.Default
    @Column(name = "was_overridden")
    private Boolean wasOverridden = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "override_value", columnDefinition = "jsonb")
    private Map<String, Object> overrideValue;

    @Column(name = "override_by", length = 100)
    private String overrideBy;

    @Column(name = "override_reason", columnDefinition = "text")
    private String overrideReason;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (wasOverridden == null) {
            wasOverridden = false;
        }
    }
}
