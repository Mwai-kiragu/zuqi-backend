package com.zuqi.api.dto.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentReportResponse {

    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal totalCollected;
    private BigDecimal totalOutstanding;
    private Long totalPayments;
    private Long unreconciledCount;
    private List<PaymentMethodSummary> byPaymentMethod;
    private List<DailyCollection> dailyCollections;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentMethodSummary {
        private String methodName;
        private String methodCode;
        private Long count;
        private BigDecimal total;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyCollection {
        private LocalDate date;
        private BigDecimal amount;
        private Long count;
    }
}
