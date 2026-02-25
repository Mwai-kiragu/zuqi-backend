package com.zuqi.domain.ai;

import com.zuqi.domain.distributor.Distributor;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ai_recommendation_embeddings", indexes = {
        @Index(name = "idx_recommendation_embeddings_recommendation", columnList = "recommendation_id"),
        @Index(name = "idx_recommendation_embeddings_distributor",    columnList = "distributor_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecommendationEmbedding {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recommendation_id", nullable = false)
    private Recommendation recommendation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "distributor_id", nullable = false)
    private Distributor distributor;

    /**
     * 768-dimensional vector embedding from nomic-embed-text via Ollama.
     * Stored as pgvector type in PostgreSQL.
     */
    @Column(name = "embedding", nullable = false, columnDefinition = "vector(768)")
    private String embedding;

    @Column(name = "recommendation_summary", columnDefinition = "TEXT")
    private String recommendationSummary;

    @Column(name = "model_version", length = 50)
    private String modelVersion;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
