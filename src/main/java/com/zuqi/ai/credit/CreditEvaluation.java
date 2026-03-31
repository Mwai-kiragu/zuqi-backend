package com.zuqi.ai.credit;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * LLM-generated credit evaluation response.
 *
 * Contains structured output from Ollama credit scoring model with:
 * - Risk score (0-100)
 * - Recommended credit limit
 * - Risk category classification
 * - Detailed reasoning for audit trail
 *
 * Blueprint reference: implementation_plan.md Phase 2 Task 2.2
 */
@Builder
public record CreditEvaluation(
        String merchantId,
        int creditScore,                    // 0-100 score (higher = lower risk)
        RiskCategory riskCategory,
        BigDecimal recommendedCreditLimit,
        BigDecimal currentCreditLimit,
        String recommendation,              // APPROVE, INCREASE, DECREASE, REJECT, MAINTAIN
        String reasoning,                   // LLM narrative explanation
        List<String> strengthFactors,       // Positive factors (e.g., "Consistent payment history")
        List<String> riskFactors,           // Negative factors (e.g., "High utilization ratio")
        List<String> recommendations,       // Actionable suggestions (e.g., "Monitor payment delays")
        LocalDateTime evaluatedAt,
        String modelVersion,                // Track which LLM version was used
        Integer mlScore,                    // Raw ML classifier score, null in LLM-only mode
        Integer llmScore                    // Raw LLM score, null in ML-only mode
) {

    public enum RiskCategory {
        VERY_LOW,       // 80-100: Excellent credit profile
        LOW,            // 60-79: Good credit profile
        MEDIUM,         // 40-59: Moderate risk
        HIGH,           // 20-39: Elevated risk
        VERY_HIGH       // 0-19: Severe risk
    }

    /**
     * Determine risk category from credit score.
     */
    public static RiskCategory determineRiskCategory(int creditScore) {
        if (creditScore >= 80) return RiskCategory.VERY_LOW;
        if (creditScore >= 60) return RiskCategory.LOW;
        if (creditScore >= 40) return RiskCategory.MEDIUM;
        if (creditScore >= 20) return RiskCategory.HIGH;
        return RiskCategory.VERY_HIGH;
    }

    /**
     * Generate recommendation action from score and current limit.
     */
    public static String determineRecommendation(
            int creditScore,
            BigDecimal currentLimit,
            BigDecimal recommendedLimit
    ) {
        if (currentLimit == null || currentLimit.compareTo(BigDecimal.ZERO) == 0) {
            // New merchant
            return creditScore >= 40 ? "APPROVE" : "REJECT";
        }

        // Existing merchant - compare recommended vs current
        int comparison = recommendedLimit.compareTo(currentLimit);

        if (comparison > 0) {
            return creditScore >= 60 ? "INCREASE" : "MAINTAIN";
        } else if (comparison < 0) {
            return creditScore < 40 ? "DECREASE" : "MAINTAIN";
        } else {
            return "MAINTAIN";
        }
    }

    /**
     * Calculate recommended credit limit based on risk score and business metrics.
     *
     * Formula:
     * - Base limit from avg order value * order frequency * 4 weeks
     * - Risk multiplier: 0.5 (VERY_HIGH) to 2.0 (VERY_LOW)
     * - Floor: KES 10,000
     * - Ceiling: KES 5,000,000
     */
    public static BigDecimal calculateRecommendedLimit(
            int creditScore,
            BigDecimal avgOrderValue,
            double orderFrequencyPerWeek
    ) {
        if (avgOrderValue == null || avgOrderValue.compareTo(BigDecimal.ZERO) == 0) {
            // Fallback for new merchants with no order history
            return BigDecimal.valueOf(50_000); // KES 50,000 starter limit
        }

        // Base credit need = avg order * orders per month
        BigDecimal ordersPerMonth = BigDecimal.valueOf(orderFrequencyPerWeek * 4);
        BigDecimal baseLimit = avgOrderValue.multiply(ordersPerMonth);

        // Apply risk multiplier
        double riskMultiplier = calculateRiskMultiplier(creditScore);
        BigDecimal adjustedLimit = baseLimit.multiply(BigDecimal.valueOf(riskMultiplier));

        // Apply floor and ceiling
        BigDecimal floor = BigDecimal.valueOf(10_000);    // KES 10,000
        BigDecimal ceiling = BigDecimal.valueOf(5_000_000); // KES 5,000,000

        if (adjustedLimit.compareTo(floor) < 0) {
            return floor;
        } else if (adjustedLimit.compareTo(ceiling) > 0) {
            return ceiling;
        } else {
            // Round to nearest 10,000
            return adjustedLimit.divide(BigDecimal.valueOf(10_000), 0, java.math.RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(10_000));
        }
    }

    /**
     * Risk multiplier based on credit score.
     */
    private static double calculateRiskMultiplier(int creditScore) {
        if (creditScore >= 80) return 2.0;      // VERY_LOW risk: 2x base
        if (creditScore >= 60) return 1.5;      // LOW risk: 1.5x base
        if (creditScore >= 40) return 1.0;      // MEDIUM risk: 1x base
        if (creditScore >= 20) return 0.75;     // HIGH risk: 0.75x base
        return 0.5;                             // VERY_HIGH risk: 0.5x base
    }
}
