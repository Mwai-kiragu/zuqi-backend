package com.zuqi.api.dto.gl;

import com.zuqi.domain.gl.AccountType;
import com.zuqi.domain.gl.NormalBalance;
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
public class TrialBalanceRow {

    private UUID accountId;
    private String accountCode;
    private String accountName;
    private AccountType accountType;
    private NormalBalance normalBalance;
    private BigDecimal periodDebit;
    private BigDecimal periodCredit;
    private BigDecimal closingDebit;
    private BigDecimal closingCredit;
}
