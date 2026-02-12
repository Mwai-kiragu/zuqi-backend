package com.zuqi.ai.credit;

import com.zuqi.ai.feature.MerchantFeatures;
import lombok.Builder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Structured merchant profile for LLM-based credit scoring.
 *
 * Transforms MerchantFeatures into a narrative-friendly format for LLM consumption.
 * Designed to provide context-rich credit evaluation input.
 *
 * Blueprint reference: implementation_plan.md Phase 2 Task 2.2
 */
@Builder
public record MerchantCreditProfile(
        String merchantId,
        String businessName,
        String businessCategory,
        int relationshipTenureDays,

        // Order behavior summary
        OrderBehavior orderBehavior,

        // Payment history summary
        PaymentHistory paymentHistory,

        // Credit utilization summary
        CreditUtilization creditUtilization,

        // Risk indicators
        RiskIndicators riskIndicators
) {

    @Builder
    public record OrderBehavior(
            int totalOrders,
            double orderFrequencyPerWeek,
            BigDecimal avgOrderValue,
            String orderTrend,              // "GROWING", "STABLE", "DECLINING"
            double orderConsistencyScore,   // 0-10 scale (lower stddev = higher score)
            int daysSinceLastOrder,
            int uniqueSkusOrdered,
            double productDiversification   // 0-1 scale (lower concentration = higher diversity)
    ) {}

    @Builder
    public record PaymentHistory(
            int totalPayments,
            double onTimePaymentPct,
            double avgDaysToPay,
            int worstDaysToPay,
            int consecutiveOnTimeStreak,
            BigDecimal totalOverdueAmount,
            String preferredPaymentMethod
    ) {}

    @Builder
    public record CreditUtilization(
            BigDecimal currentCreditLimit,
            double currentUtilizationPct,
            double peakUtilizationPct,
            String utilizationTrend,        // "INCREASING", "STABLE", "DECREASING"
            int limitIncreaseCount,
            int daysSinceLastLimitChange
    ) {}

    @Builder
    public record RiskIndicators(
            double cancellationRate,
            double returnRate,
            double partialPaymentFrequency,
            boolean hasOverdueBalance,
            String verificationStatus,
            String geographicRisk            // "LOW", "MEDIUM", "HIGH" based on cluster default rates
    ) {}

    /**
     * Transform MerchantFeatures into LLM-friendly credit profile.
     */
    public static MerchantCreditProfile fromFeatures(
            MerchantFeatures features,
            String businessName,
            String geographicRisk
    ) {
        return MerchantCreditProfile.builder()
                .merchantId(features.merchantId().toString())
                .businessName(businessName)
                .businessCategory(features.businessCategoryEncoded())
                .relationshipTenureDays(features.relationshipTenureDays())
                .orderBehavior(buildOrderBehavior(features))
                .paymentHistory(buildPaymentHistory(features))
                .creditUtilization(buildCreditUtilization(features))
                .riskIndicators(buildRiskIndicators(features, geographicRisk))
                .build();
    }

    private static OrderBehavior buildOrderBehavior(MerchantFeatures features) {
        String orderTrend = determineOrderTrend(features.orderValueTrendSlope12w());
        double consistencyScore = calculateConsistencyScore(features.orderConsistencyStddev(), features.avgOrderValue());
        double diversification = 1.0 - (features.topSkuConcentration() != null ? features.topSkuConcentration() : 0.0);

        return OrderBehavior.builder()
                .totalOrders(features.totalOrders() != null ? features.totalOrders() : 0)
                .orderFrequencyPerWeek(features.orderFrequencyPerWeek() != null ? features.orderFrequencyPerWeek() : 0.0)
                .avgOrderValue(features.avgOrderValue() != null ? features.avgOrderValue() : BigDecimal.ZERO)
                .orderTrend(orderTrend)
                .orderConsistencyScore(consistencyScore)
                .daysSinceLastOrder(features.daysSinceLastOrder() != null ? features.daysSinceLastOrder() : 0)
                .uniqueSkusOrdered(features.uniqueSkusOrdered() != null ? features.uniqueSkusOrdered() : 0)
                .productDiversification(diversification)
                .build();
    }

    private static PaymentHistory buildPaymentHistory(MerchantFeatures features) {
        String preferredMethod = determinePreferredPaymentMethod(features.paymentMethodDistribution());

        return PaymentHistory.builder()
                .totalPayments(features.totalPayments() != null ? features.totalPayments() : 0)
                .onTimePaymentPct(features.onTimePaymentPct() != null ? features.onTimePaymentPct() : 0.0)
                .avgDaysToPay(features.avgDaysToPay() != null ? features.avgDaysToPay() : 0.0)
                .worstDaysToPay(features.worstDaysToPay() != null ? features.worstDaysToPay() : 0)
                .consecutiveOnTimeStreak(features.consecutiveOnTimeStreak() != null ? features.consecutiveOnTimeStreak() : 0)
                .totalOverdueAmount(features.totalOverdueAmount() != null ? features.totalOverdueAmount() : BigDecimal.ZERO)
                .preferredPaymentMethod(preferredMethod)
                .build();
    }

    private static CreditUtilization buildCreditUtilization(MerchantFeatures features) {
        String utilizationTrend = determineUtilizationTrend(features.utilizationTrendSlope());

        return CreditUtilization.builder()
                .currentCreditLimit(features.currentCreditLimit() != null ? features.currentCreditLimit() : BigDecimal.ZERO)
                .currentUtilizationPct(
                        features.currentUtilizationRatio() != null
                                ? features.currentUtilizationRatio() * 100
                                : 0.0
                )
                .peakUtilizationPct(
                        features.peakUtilizationRatio() != null
                                ? features.peakUtilizationRatio() * 100
                                : 0.0
                )
                .utilizationTrend(utilizationTrend)
                .limitIncreaseCount(features.limitIncreaseCount() != null ? features.limitIncreaseCount() : 0)
                .daysSinceLastLimitChange(features.daysSinceLastLimitChange() != null ? features.daysSinceLastLimitChange() : 0)
                .build();
    }

    private static RiskIndicators buildRiskIndicators(MerchantFeatures features, String geographicRisk) {
        boolean hasOverdue = features.totalOverdueAmount() != null
                && features.totalOverdueAmount().compareTo(BigDecimal.ZERO) > 0;

        return RiskIndicators.builder()
                .cancellationRate(features.cancellationRate() != null ? features.cancellationRate() : 0.0)
                .returnRate(features.returnRate() != null ? features.returnRate() : 0.0)
                .partialPaymentFrequency(features.partialPaymentFrequency() != null ? features.partialPaymentFrequency() : 0.0)
                .hasOverdueBalance(hasOverdue)
                .verificationStatus(features.verificationStatus() != null ? features.verificationStatus() : "UNVERIFIED")
                .geographicRisk(geographicRisk != null ? geographicRisk : "UNKNOWN")
                .build();
    }

    // Helper methods

    private static String determineOrderTrend(Double slope) {
        if (slope == null) return "UNKNOWN";
        if (slope > 0.1) return "GROWING";
        if (slope < -0.1) return "DECLINING";
        return "STABLE";
    }

    private static String determineUtilizationTrend(Double slope) {
        if (slope == null) return "UNKNOWN";
        if (slope > 0.05) return "INCREASING";
        if (slope < -0.05) return "DECREASING";
        return "STABLE";
    }

    private static double calculateConsistencyScore(Double stddev, BigDecimal avgValue) {
        if (stddev == null || avgValue == null || avgValue.compareTo(BigDecimal.ZERO) == 0) {
            return 5.0; // Neutral score
        }

        // Coefficient of variation (CV) = stddev / mean
        double cv = stddev / avgValue.doubleValue();

        // Convert CV to 0-10 score (lower CV = higher consistency)
        // CV > 1.0 → score 0
        // CV = 0.0 → score 10
        double score = Math.max(0, 10 - (cv * 10));
        return BigDecimal.valueOf(score).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private static String determinePreferredPaymentMethod(Map<String, Integer> distribution) {
        if (distribution == null || distribution.isEmpty()) {
            return "UNKNOWN";
        }

        return distribution.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("UNKNOWN");
    }
}
