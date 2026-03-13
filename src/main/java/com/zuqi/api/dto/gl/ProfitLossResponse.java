package com.zuqi.api.dto.gl;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class ProfitLossResponse {
    private LocalDate fromDate;
    private LocalDate toDate;
    private ProfitLossSection revenue;
    private ProfitLossSection costOfGoods;
    private ProfitLossSection expenses;
    private BigDecimal grossProfit;
    private BigDecimal netIncome;
}
