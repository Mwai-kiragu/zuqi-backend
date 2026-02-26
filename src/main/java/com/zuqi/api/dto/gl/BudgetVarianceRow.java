package com.zuqi.api.dto.gl;

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
public class BudgetVarianceRow {

    private UUID accountId;
    private String accountCode;
    private String accountName;
    private int periodMonth;
    private BigDecimal budgetedAmount;
    private BigDecimal actualAmount;
    private BigDecimal variance;
    private BigDecimal variancePct;
}
