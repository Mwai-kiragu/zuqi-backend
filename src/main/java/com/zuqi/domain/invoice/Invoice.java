package com.zuqi.domain.invoice;

import com.zuqi.domain.distributor.Distributor;
import com.zuqi.domain.customer.Customer;
import com.zuqi.domain.order.Order;
import com.zuqi.domain.pos.PosSale;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "invoices", indexes = {
        @Index(name = "idx_invoices_order", columnList = "order_id"),
        @Index(name = "idx_invoices_distributor", columnList = "distributor_id"),
        @Index(name = "idx_invoices_merchant", columnList = "merchant_id"),
        @Index(name = "idx_invoices_status", columnList = "status"),
        @Index(name = "idx_invoices_due_date", columnList = "due_date"),
        @Index(name = "idx_invoices_created", columnList = "created_at")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "invoice_number", nullable = false, unique = true, length = 50)
    private String invoiceNumber;

    @OneToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "order_id", nullable = true)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pos_sale_id", nullable = true)
    private PosSale posOrder;

    @Column(name = "source_type", nullable = false, length = 20)
    @Builder.Default
    private String sourceType = "ORDER";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "distributor_id", nullable = false)
    private Distributor distributor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_id", nullable = true)
    private Customer merchant;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "discount_amount", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "tax_amount", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "paid_amount", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Column(name = "balance_due", precision = 15, scale = 2)
    private BigDecimal balanceDue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private InvoiceStatus status = InvoiceStatus.DRAFT;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "viewed_at")
    private LocalDateTime viewedAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "recipient_email")
    private String recipientEmail;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "terms_and_conditions", columnDefinition = "TEXT")
    private String termsAndConditions;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<InvoiceItem> invoiceItems = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    @Version
    private Long version;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Helper methods
    public void calculateBalanceDue() {
        this.balanceDue = this.totalAmount.subtract(this.paidAmount != null ? this.paidAmount : BigDecimal.ZERO);
    }

    public void markAsSent(String email) {
        this.status = InvoiceStatus.SENT;
        this.sentAt = LocalDateTime.now();
        this.recipientEmail = email;
    }

    public void markAsViewed() {
        if (this.viewedAt == null) {
            this.viewedAt = LocalDateTime.now();
            if (this.status == InvoiceStatus.SENT) {
                this.status = InvoiceStatus.VIEWED;
            }
        }
    }

    public void recordPayment(BigDecimal amount) {
        this.paidAmount = this.paidAmount.add(amount);
        this.calculateBalanceDue();

        if (this.balanceDue.compareTo(BigDecimal.ZERO) <= 0) {
            this.status = InvoiceStatus.PAID;
            this.paidAt = LocalDateTime.now();
        } else {
            this.status = InvoiceStatus.PARTIALLY_PAID;
        }
    }

    /**
     * Apply a credit note: reduces the invoice total by the credited amount,
     * then re-derives status from the new balance. Unlike recordPayment this
     * does NOT touch paidAmount — it adjusts what was originally owed.
     */
    public void applyCredit(BigDecimal amount) {
        this.totalAmount = this.totalAmount.subtract(amount);
        if (this.totalAmount.compareTo(BigDecimal.ZERO) < 0) {
            this.totalAmount = BigDecimal.ZERO;
        }
        this.calculateBalanceDue();

        if (this.status == InvoiceStatus.CANCELLED) return;

        if (this.balanceDue.compareTo(BigDecimal.ZERO) <= 0) {
            this.status = InvoiceStatus.PAID;
            if (this.paidAt == null) {
                this.paidAt = java.time.LocalDateTime.now();
            }
        } else if (this.paidAmount != null && this.paidAmount.compareTo(BigDecimal.ZERO) > 0) {
            this.status = InvoiceStatus.PARTIALLY_PAID;
        }
        // If no cash has been paid yet, preserve the current open status
        // (SENT / VIEWED / UNPAID / OVERDUE) — only the amount owed has changed.
    }

    public boolean isOverdue() {
        return this.dueDate.isBefore(LocalDate.now())
                && this.status != InvoiceStatus.PAID
                && this.status != InvoiceStatus.CANCELLED;
    }
}
