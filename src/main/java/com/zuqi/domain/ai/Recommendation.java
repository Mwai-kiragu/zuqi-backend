package com.zuqi.domain.ai;

import com.zuqi.domain.distributor.Distributor;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ai_recommendations", indexes = {
        @Index(name = "idx_recommendations_distributor", columnList = "distributor_id, status, created_at DESC"),
        @Index(name = "idx_recommendations_status",      columnList = "status, priority DESC"),
        @Index(name = "idx_recommendations_priority",    columnList = "priority, status"),
        @Index(name = "idx_recommendations_type",        columnList = "recommendation_type, created_at DESC"),
        @Index(name = "idx_recommendations_created",     columnList = "created_at DESC")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Recommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "distributor_id", nullable = false)
    private Distributor distributor;

    @Enumerated(EnumType.STRING)
    @Column(name = "recommendation_type", nullable = false, length = 50)
    private RecommendationType recommendationType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String observation;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> evidence;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String recommendation;

    @Column(name = "expected_impact", columnDefinition = "TEXT")
    private String expectedImpact;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RecommendationPriority priority;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RecommendationStatus status = RecommendationStatus.PENDING;

    @Column(name = "acted_on_at")
    private LocalDateTime actedOnAt;

    @Column(columnDefinition = "TEXT")
    private String outcome;

    @Column(name = "model_version", length = 50)
    private String modelVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = RecommendationStatus.PENDING;
        }
    }
}
