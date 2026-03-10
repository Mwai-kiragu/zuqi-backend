package com.zuqi.domain.expense;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "expenses", indexes = {
        @Index(name = "idx_expenses_distributor", columnList = "distributor_id"),
        @Index(name = "idx_expenses_status", columnList = "distributor_id, status"),
        @Index(name = "idx_expenses_date", columnList = "distributor_id, expense_date"),
        @Index(name = "idx_expenses_category", columnList = "distributor_id, category")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "distributor_id", nullable = false)
    private UUID distributorId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ExpenseCategory category;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ExpenseStatus status = ExpenseStatus.DRAFT;

    @Column(name = "reference_number", length = 100)
    private String referenceNumber;

    @Column(name = "receipt_url", columnDefinition = "TEXT")
    private String receiptUrl;

    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "gl_entry_id")
    private UUID glEntryId;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
