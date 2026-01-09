package com.zuqi.api.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevenueChartData {
    private List<ChartDataPoint> daily;
    private List<ChartDataPoint> monthly;
    private BigDecimal totalPeriod;
    private BigDecimal averageDaily;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChartDataPoint {
        private String label;
        private BigDecimal revenue;
        private Long orderCount;
    }
}
