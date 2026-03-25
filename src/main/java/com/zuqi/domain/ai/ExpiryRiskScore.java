package com.zuqi.domain.ai;

import com.zuqi.domain.distributor.Distributor;
import com.zuqi.domain.inventory.ProductBatch;
import com.zuqi.domain.inventory.Warehouse;
import com.zuqi.domain.product.Product;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ai_expiry_risk_scores", indexes = {
        @Index(name = "idx_ai_expiry_risk_distributor_warehouse", columnList = "distributor_id, warehouse_id"),
        @Index(name = "idx_ai_expiry_risk_distributor_product", columnList = "distributor_id, product_id"),
        @Index(name = "idx_ai_expiry_risk_expiry_date", columnList = "expiry_date"),
        @Index(name = "idx_ai_expiry_risk_tier", columnList = "distributor_id, risk_tier")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpiryRiskScore {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "distributor_id", nullable = false)
    private Distributor distributor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id")
    private ProductBatch batch;

    @Column(name = "batch_number", length = 100)
    private String batchNumber;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    @Column(name = "days_to_expiry")
    private Integer daysToExpiry;

    @Column(name = "current_stock_qty")
    private Double currentStockQty;

    @Column(name = "avg_daily_sales_rate")
    private Double avgDailySalesRate;

    @Column(name = "projected_days_to_sell")
    private Double projectedDaysToSell;

    @Column(name = "sell_through_probability")
    private Double sellThroughProbability;

    @Column(name = "risk_score", nullable = false)
    private Double riskScore;

    @Column(name = "risk_tier", length = 20)
    private String riskTier;

    @Column(name = "recommended_action", length = 30)
    private String recommendedAction;

    @Column(name = "discount_suggestion_pct")
    private Double discountSuggestionPct;

    @Column(name = "confidence_score")
    private Double confidenceScore;

    @Column(name = "data_phase", length = 20)
    private String dataPhase;

    @Column(name = "model_version")
    private Integer modelVersion;

    @Column(name = "computed_at")
    private LocalDateTime computedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
