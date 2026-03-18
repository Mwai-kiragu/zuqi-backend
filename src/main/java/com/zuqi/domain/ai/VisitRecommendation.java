package com.zuqi.domain.ai;

import com.zuqi.domain.customer.Customer;
import com.zuqi.domain.distributor.Distributor;
import com.zuqi.domain.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Visit recommendation entity.
 * AI-generated guidance for a sales rep on the optimal day and frequency
 * to visit a customer, based on predicted conversion probability.
 *
 * Table: ai_visit_recommendations
 */
@Entity
@Table(name = "ai_visit_recommendations", uniqueConstraints = {
        @UniqueConstraint(name = "uq_visit_recommendation",
                columnNames = {"distributor_id", "sales_rep_id", "customer_id"})
}, indexes = {
        @Index(name = "idx_visit_rec_rep_conversion", columnList = "sales_rep_id, predicted_conversion"),
        @Index(name = "idx_visit_rec_distributor_customer", columnList = "distributor_id, customer_id")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VisitRecommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "distributor_id", nullable = false)
    private Distributor distributor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sales_rep_id", nullable = false)
    private User salesRep;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    /** 1 = Monday … 7 = Sunday */
    @Column(name = "recommended_day")
    private Integer recommendedDay;

    @Column(name = "predicted_conversion")
    private Double predictedConversion;

    @Column(name = "recommended_frequency_per_week")
    private Double recommendedFrequencyPerWeek;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "data_phase", length = 20)
    private String dataPhase;

    @Column(name = "model_version")
    private Integer modelVersion;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
