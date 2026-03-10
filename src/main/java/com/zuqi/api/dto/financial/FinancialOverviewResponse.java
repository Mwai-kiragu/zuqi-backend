package com.zuqi.api.dto.financial;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class FinancialOverviewResponse {

    private BigDecimal totalRevenue;
    private BigDecimal totalExpenses;
    private BigDecimal netIncome;
    private BigDecimal profitMarginPct;
    private BigDecimal arBalance;
    private BigDecimal apBalance;
    private BigDecimal cashPosition;
    private List<MonthlyData> monthlyBreakdown;
    private List<CategoryData> expenseByCategory;
    private String fromDate;
    private String toDate;

    @Data
    @Builder
    public static class MonthlyData {
        private int year;
        private int month;
        private String monthLabel;  // e.g. "Jan 2026"
        private BigDecimal revenue;
        private BigDecimal expenses;
    }

    @Data
    @Builder
    public static class CategoryData {
        private String category;
        private BigDecimal amount;
        private BigDecimal percentage;
    }
}
