package com.zuqi.domain.returns;

import com.zuqi.domain.invoice.Invoice;
import com.zuqi.domain.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "credit_note_applications", indexes = {
        @Index(name = "idx_credit_note_apps_cn",      columnList = "credit_note_id"),
        @Index(name = "idx_credit_note_apps_invoice", columnList = "invoice_id")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditNoteApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "credit_note_id", nullable = false)
    private CreditNote creditNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Column(name = "amount_applied", nullable = false, precision = 15, scale = 2)
    private BigDecimal amountApplied;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applied_by_id")
    private User appliedBy;

    @CreatedDate
    @Column(name = "applied_at", nullable = false, updatable = false)
    private LocalDateTime appliedAt;
}
