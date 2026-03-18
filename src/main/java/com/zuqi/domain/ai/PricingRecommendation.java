package com.zuqi.domain.ai;

import com.zuqi.domain.distributor.Distributor;
import com.zuqi.domain.product.Product;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Pricing recommendation entity.
 * Stores AI-generated sell-price suggestions for a product, including
 * predicted demand impact and estimated revenue uplift in KES.
 *
 * Table: ai_pricing_recommendations
 */
@Entity
@Table(name = "ai_pricing_recommendations", indexes = {
        @Index(name = "idx_pricing_rec_distributor_product", columnList = "distributor_id, product_id"),
        @Index(name = "idx_pricing_rec_distributor_status", columnList = "distributor_id, status"),
        @Index(name = "idx_pricing_rec_product", columnList = "product_id")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PricingRecommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "distributor_id", nullable = false)
    private Distributor distributor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "current_price")
    private Double currentPrice;

    @Column(name = "recommended_price")
    private Double recommendedPrice;

    @Column(name = "price_change_pct")
    private Double priceChangePct;

    @Column(name = "predicted_demand_at_current")
    private Double predictedDemandAtCurrent;

    @Column(name = "predicted_demand_at_recommended")
    private Double predictedDemandAtRecommended;

    @Column(name = "estimated_revenue_impact_kes")
    private Double estimatedRevenueImpactKes;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "confidence_score")
    private Double confidenceScore;

    @Column(name = "data_phase", length = 20)
    private String dataPhase;

    @Column(name = "status", length = 20)
    private String status;

    @Column(name = "model_version")
    private Integer modelVersion;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
