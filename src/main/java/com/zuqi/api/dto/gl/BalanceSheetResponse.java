package com.zuqi.api.dto.gl;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class BalanceSheetResponse {
    private LocalDate asOfDate;
    private BalanceSheetSection assets;
    private BalanceSheetSection liabilities;
    private BalanceSheetSection equity;
    private BigDecimal totalLiabilitiesAndEquity;
}
