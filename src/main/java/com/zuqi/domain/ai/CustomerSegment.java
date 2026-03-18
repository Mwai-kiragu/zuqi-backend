package com.zuqi.domain.ai;

import com.zuqi.domain.customer.Customer;
import com.zuqi.domain.distributor.Distributor;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Customer segment entity.
 * Stores the ML-assigned segment (cluster) for each customer, enabling
 * targeted marketing and tailored sales strategies.
 *
 * Table: ai_customer_segments
 */
@Entity
@Table(name = "ai_customer_segments", uniqueConstraints = {
        @UniqueConstraint(name = "uq_customer_segment", columnNames = {"distributor_id", "customer_id"})
}, indexes = {
        @Index(name = "idx_customer_segment_distributor_label", columnList = "distributor_id, segment_label"),
        @Index(name = "idx_customer_segment_customer", columnList = "customer_id")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerSegment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "distributor_id", nullable = false)
    private Distributor distributor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "segment_id")
    private Integer segmentId;

    @Column(name = "segment_label", length = 50)
    private String segmentLabel;

    @Column(name = "confidence_score")
    private Double confidenceScore;

    @Column(name = "data_phase", length = 20)
    private String dataPhase;

    @Column(name = "computed_at")
    private LocalDateTime computedAt;
}
