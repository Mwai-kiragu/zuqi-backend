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
 * Bank reconciliation feedback entity.
 * Captures human corrections to AI-generated bank reconciliation matches,
 * used as training signal for model improvement.
 *
 * Table: ai_bank_recon_feedback
 */
@Entity
@Table(name = "ai_bank_recon_feedback", indexes = {
        @Index(name = "idx_bank_recon_feedback_distributor", columnList = "distributor_id"),
        @Index(name = "idx_bank_recon_feedback_match", columnList = "match_id"),
        @Index(name = "idx_bank_recon_feedback_accepted", columnList = "distributor_id, accepted")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankReconFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "distributor_id", nullable = false)
    private Distributor distributor;

    @Column(name = "match_id")
    private UUID matchId;

    @Column(name = "accepted", nullable = false)
    private Boolean accepted;

    @Column(name = "corrected_entity_id")
    private UUID correctedEntityId;

    @Column(name = "corrected_entity_type", length = 50)
    private String correctedEntityType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer merchant;

    @Column(name = "amount")
    private Double amount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
