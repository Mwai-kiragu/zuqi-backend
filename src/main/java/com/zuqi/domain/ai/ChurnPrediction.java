package com.zuqi.domain.ai;

import com.zuqi.domain.customer.Customer;
import com.zuqi.domain.distributor.Distributor;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Churn prediction entity.
 * Stores the ML-computed probability that a customer will churn, along with
 * risk tiering, key churn drivers and recommended retention action.
 *
 * Table: ai_churn_predictions
 */
@Entity
@Table(name = "ai_churn_predictions", uniqueConstraints = {
        @UniqueConstraint(name = "uq_churn_prediction", columnNames = {"distributor_id", "customer_id"})
}, indexes = {
        @Index(name = "idx_churn_prediction_distributor_tier", columnList = "distributor_id, risk_tier"),
        @Index(name = "idx_churn_prediction_distributor_prob", columnList = "distributor_id, churn_probability"),
        @Index(name = "idx_churn_prediction_customer", columnList = "customer_id")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChurnPrediction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "distributor_id", nullable = false)
    private Distributor distributor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "churn_probability")
    private Double churnProbability;

    @Column(name = "risk_tier", length = 20)
    private String riskTier;

    @Column(name = "days_since_last_order")
    private Integer daysSinceLastOrder;

    @Column(name = "top_churn_factor", length = 100)
    private String topChurnFactor;

    @Column(name = "recommended_action", columnDefinition = "TEXT")
    private String recommendedAction;

    @Column(name = "confidence_score")
    private Double confidenceScore;

    @Column(name = "data_phase", length = 20)
    private String dataPhase;

    @Column(name = "model_version")
    private Integer modelVersion;

    @Column(name = "computed_at")
    private LocalDateTime computedAt;
}
