package com.zuqi.api.dto.gl;

import com.zuqi.domain.gl.AccountType;
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
    private AccountType accountType;
    private int periodMonth;
    private BigDecimal budgetedAmount;
    private BigDecimal actualAmount;
    private BigDecimal variance;
    private BigDecimal variancePct;
}
