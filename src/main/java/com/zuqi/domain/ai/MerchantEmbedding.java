package com.zuqi.domain.ai;

import com.zuqi.domain.customer.Customer;
import com.zuqi.domain.distributor.Distributor;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * pgvector embedding for merchant profile (RAG for credit scoring).
 *
 * Stores 768-dimensional vector embeddings of merchant features
 * to enable semantic similarity search during credit evaluation.
 *
 * Blueprint reference: plan.md Section 6.3, implementation_plan.md Task 2.3
 */
@Entity
@Table(name = "ai_merchant_embeddings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MerchantEmbedding {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Customer merchant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "distributor_id", nullable = false)
    private Distributor distributor;

    /**
     * Vector embedding from RBS AI embedding model.
     * Stored as pgvector type in PostgreSQL.
     */
    @Column(name = "embedding", nullable = false, columnDefinition = "vector(768)")
    private String embedding;  // Stored as string representation: "[0.1, 0.2, ...]"

    @Column(name = "feature_summary", columnDefinition = "TEXT")
    private String featureSummary;

    @Column(name = "model_version", length = 50)
    private String modelVersion;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
