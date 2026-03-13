package com.zuqi.api.dto.accounting;

import com.zuqi.domain.accounting.BankReconciliationItem;
import com.zuqi.domain.accounting.ReconciliationItemType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class ReconciliationItemResponse {
    private UUID id;
    private ReconciliationItemType itemType;
    private String description;
    private BigDecimal amount;
    private LocalDate transactionDate;
    private String reference;
    private boolean isCleared;

    public static ReconciliationItemResponse from(BankReconciliationItem item) {
        return ReconciliationItemResponse.builder()
                .id(item.getId())
                .itemType(item.getItemType())
                .description(item.getDescription())
                .amount(item.getAmount())
                .transactionDate(item.getTransactionDate())
                .reference(item.getReference())
                .isCleared(item.isCleared())
                .build();
    }
}
