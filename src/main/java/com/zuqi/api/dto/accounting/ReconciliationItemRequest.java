package com.zuqi.api.dto.accounting;

import com.zuqi.domain.accounting.ReconciliationItemType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ReconciliationItemRequest {
    @NotNull
    private ReconciliationItemType itemType;
    @NotBlank
    private String description;
    @NotNull
    private BigDecimal amount;
    private LocalDate transactionDate;
    private String reference;
    private boolean isCleared;
}
