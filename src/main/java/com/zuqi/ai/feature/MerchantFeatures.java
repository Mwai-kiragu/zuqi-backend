package com.zuqi.ai.feature;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Complete feature set for a single merchant.
 * Used by credit scoring, churn prediction, and other merchant-level AI models.
 *
 * Features are computed from:
 * - Order history
 * - Payment history
 * - Credit limits
 * - Merchant profile
 *
 * Blueprint reference: plan.md Section 4.2 - MerchantFeatureService
 */
@Builder
public record MerchantFeatures(
        UUID merchantId,
        LocalDateTime computedAt,

        // Order features
        Integer totalOrders,
        Double orderFrequencyPerWeek,
        BigDecimal avgOrderValue,
        Double orderValueTrendSlope12w,          // Linear regression slope over 12 weeks
        Double orderConsistencyStddev,            // Standard deviation of order values
        Double cancellationRate,
        Double returnRate,
        Integer daysSinceLastOrder,
        Integer uniqueSkusOrdered,
        Double topSkuConcentration,               // % of orders from top SKU

        // Payment features
        Integer totalPayments,
        Double onTimePaymentPct,
        Double avgDaysToPay,
        Integer worstDaysToPay,
        Double partialPaymentFrequency,
        Map<String, Integer> paymentMethodDistribution,  // {"MPESA": 45, "CASH": 30, "BANK": 25}
        Integer consecutiveOnTimeStreak,
        BigDecimal totalOverdueAmount,

        // Credit features
        BigDecimal currentCreditLimit,
        Double currentUtilizationRatio,           // current_balance / credit_limit
        Double peakUtilizationRatio,              // historical peak
        Double utilizationTrendSlope,             // Trending up/down
        Integer limitIncreaseCount,
        Integer daysSinceLastLimitChange,

        // Profile features
        String businessCategoryEncoded,           // Category as string for encoding
        Integer relationshipTenureDays,           // Days since merchant creation
        String verificationStatus,                // "VERIFIED", "PENDING", "UNVERIFIED"
        String geographicCluster                  // City/region cluster ID
) {
}
