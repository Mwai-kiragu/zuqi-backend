package com.zuqi.api.dto.expense;

import com.zuqi.domain.expense.Expense;
import com.zuqi.domain.expense.ExpenseCategory;
import com.zuqi.domain.expense.ExpenseStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class ExpenseResponse {

    private UUID id;
    private UUID distributorId;
    private String title;
    private String description;
    private ExpenseCategory category;
    private BigDecimal amount;
    private LocalDate expenseDate;
    private ExpenseStatus status;
    private String referenceNumber;
    private String receiptUrl;
    private String paymentMethod;
    private LocalDateTime paidAt;
    private UUID glEntryId;
    private UUID createdBy;
    private UUID approvedBy;
    private LocalDateTime approvedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ExpenseResponse from(Expense e) {
        return ExpenseResponse.builder()
                .id(e.getId())
                .distributorId(e.getDistributorId())
                .title(e.getTitle())
                .description(e.getDescription())
                .category(e.getCategory())
                .amount(e.getAmount())
                .expenseDate(e.getExpenseDate())
                .status(e.getStatus())
                .referenceNumber(e.getReferenceNumber())
                .receiptUrl(e.getReceiptUrl())
                .paymentMethod(e.getPaymentMethod())
                .paidAt(e.getPaidAt())
                .glEntryId(e.getGlEntryId())
                .createdBy(e.getCreatedBy())
                .approvedBy(e.getApprovedBy())
                .approvedAt(e.getApprovedAt())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }
}
