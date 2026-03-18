package com.zuqi.domain.ai;

import com.zuqi.domain.customer.Customer;
import com.zuqi.domain.distributor.Distributor;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Customer lifetime value entity.
 * Stores the ML-predicted 12-month revenue for each customer with
 * confidence interval bounds, used for strategic account prioritisation.
 *
 * Table: ai_customer_clv
 */
@Entity
@Table(name = "ai_customer_clv", uniqueConstraints = {
        @UniqueConstraint(name = "uq_customer_clv", columnNames = {"distributor_id", "customer_id"})
}, indexes = {
        @Index(name = "idx_customer_clv_distributor_revenue", columnList = "distributor_id, predicted_revenue_12m"),
        @Index(name = "idx_customer_clv_customer", columnList = "customer_id")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerClv {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "distributor_id", nullable = false)
    private Distributor distributor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "predicted_revenue_12m")
    private Double predictedRevenue12m;

    @Column(name = "lower_bound")
    private Double lowerBound;

    @Column(name = "upper_bound")
    private Double upperBound;

    @Column(name = "confidence_score")
    private Double confidenceScore;

    @Column(name = "data_phase", length = 20)
    private String dataPhase;

    @Column(name = "model_version")
    private Integer modelVersion;

    @Column(name = "computed_at")
    private LocalDateTime computedAt;
}
