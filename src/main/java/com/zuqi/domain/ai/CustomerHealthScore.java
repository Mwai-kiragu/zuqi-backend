package com.zuqi.domain.ai;

import com.zuqi.domain.customer.Customer;
import com.zuqi.domain.distributor.Distributor;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Customer health score entity.
 * Composite score aggregating order frequency, payment timeliness,
 * revenue trend, engagement and credit health dimensions into a
 * single health tier for field-sales prioritisation.
 *
 * Table: ai_customer_health_scores
 */
@Entity
@Table(name = "ai_customer_health_scores", uniqueConstraints = {
        @UniqueConstraint(name = "uq_customer_health", columnNames = {"distributor_id", "customer_id"})
}, indexes = {
        @Index(name = "idx_customer_health_distributor_tier", columnList = "distributor_id, health_tier"),
        @Index(name = "idx_customer_health_customer", columnList = "customer_id")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerHealthScore {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "distributor_id", nullable = false)
    private Distributor distributor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "health_score")
    private Double healthScore;

    @Column(name = "health_tier", length = 30)
    private String healthTier;

    @Column(name = "order_frequency_score")
    private Double orderFrequencyScore;

    @Column(name = "payment_timeliness_score")
    private Double paymentTimelinessScore;

    @Column(name = "revenue_trend_score")
    private Double revenueTrendScore;

    @Column(name = "engagement_score")
    private Double engagementScore;

    @Column(name = "credit_health_score")
    private Double creditHealthScore;

    @Column(name = "data_phase", length = 20)
    private String dataPhase;

    @Column(name = "computed_at")
    private LocalDateTime computedAt;
}
