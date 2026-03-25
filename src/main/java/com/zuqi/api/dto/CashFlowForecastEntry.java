package com.zuqi.api.dto;

import java.math.BigDecimal;

/**
 * One day's projected cash flow for the forecast chart.
 */
public record CashFlowForecastEntry(
        String      date,                // ISO date YYYY-MM-DD
        BigDecimal  expectedInflow,
        BigDecimal  optimisticInflow,
        BigDecimal  pessimisticInflow,
        BigDecimal  expectedOutflow,
        BigDecimal  netCash,
        BigDecimal  runningBalance,
        boolean     isShortfall
) {}
