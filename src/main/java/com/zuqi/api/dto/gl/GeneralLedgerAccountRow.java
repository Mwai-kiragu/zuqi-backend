package com.zuqi.api.dto.gl;

import com.zuqi.domain.gl.AccountType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class GeneralLedgerAccountRow {
    private UUID accountId;
    private String accountCode;
    private String accountName;
    private AccountType accountType;
    private BigDecimal openingBalance;
    private BigDecimal totalDebit;
    private BigDecimal totalCredit;
    private BigDecimal closingBalance;
    private List<GeneralLedgerLine> lines;
}
