package com.zuqi.api.dto.gl;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetRequest {

    @NotNull(message = "Account ID is required")
    private UUID accountId;

    private UUID costCenterId;

    @NotNull(message = "Budgeted amount is required")
    @Positive(message = "Budgeted amount must be positive")
    private BigDecimal budgetedAmount;

    private String notes;
}
