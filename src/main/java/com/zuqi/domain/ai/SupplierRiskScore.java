package com.zuqi.domain.ai;

import com.zuqi.domain.distributor.Distributor;
import com.zuqi.domain.supplier.Supplier;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Supplier risk score entity.
 * Composite ML-derived risk rating for a supplier covering delivery
 * reliability, quality, price consistency and responsiveness dimensions.
 *
 * Table: ai_supplier_risk_scores
 */
@Entity
@Table(name = "ai_supplier_risk_scores", uniqueConstraints = {
        @UniqueConstraint(name = "uq_supplier_risk", columnNames = {"distributor_id", "supplier_id"})
}, indexes = {
        @Index(name = "idx_supplier_risk_distributor_tier", columnList = "distributor_id, risk_tier"),
        @Index(name = "idx_supplier_risk_supplier", columnList = "supplier_id")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupplierRiskScore {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "distributor_id", nullable = false)
    private Distributor distributor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Column(name = "risk_score")
    private Double riskScore;

    @Column(name = "risk_tier", length = 20)
    private String riskTier;

    @Column(name = "delivery_reliability_score")
    private Double deliveryReliabilityScore;

    @Column(name = "quality_score")
    private Double qualityScore;

    @Column(name = "price_consistency_score")
    private Double priceConsistencyScore;

    @Column(name = "responsiveness_score")
    private Double responsivenessScore;

    @Column(name = "data_phase", length = 20)
    private String dataPhase;

    @Column(name = "computed_at")
    private LocalDateTime computedAt;
}
