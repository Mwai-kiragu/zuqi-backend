package com.zuqi.api.dto.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalesReportResponse {

    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal totalRevenue;
    private Long totalOrders;
    private BigDecimal averageOrderValue;
    private List<DailyData> dailyData;
    private List<SalesRepData> salesRepPerformance;
    private List<InvoiceSummary> invoices;
    private List<ProductSoldData> productsSold;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyData {
        private LocalDate date;
        private BigDecimal revenue;
        private Long orderCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SalesRepData {
        private String salesRepId;
        private String salesRepName;
        private Long orderCount;
        private BigDecimal totalRevenue;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InvoiceSummary {
        private String invoiceId;
        private String invoiceNumber;
        private String customerName;
        private LocalDate issueDate;
        private LocalDate dueDate;
        private BigDecimal totalAmount;
        private String status;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductSoldData {
        private String productId;
        private String productName;
        private String productSku;
        private BigDecimal totalQuantity;
        private BigDecimal totalRevenue;
    }
}
