package com.zuqi.ai.feature;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Merchant-level payment trend features for distress/default prediction.
 * Used by PaymentDistressClassifier to identify merchants at risk of default.
 *
 * Features capture 3-month trends in:
 * - Payment timing behavior
 * - Order frequency changes
 * - Credit utilization patterns
 * - Partial payment frequency
 * - Order value trends
 *
 * Blueprint reference: plan.md Section 4.2 - PaymentFeatureService
 */
@Builder
public record MerchantPaymentTrendFeatures(
        UUID merchantId,
        LocalDateTime computedAt,

        // Payment timing trends (3-month window)
        Double daysToPayTrend3m,                       // Linear regression slope of days-to-pay over 3 months
        Double daysToPayStddev3m,                      // Std deviation of days-to-pay (volatility indicator)
        Double latePaymentRate3m,                      // % of payments that were late in last 3 months
        Double latePaymentRateTrend3m,                 // Change in late payment rate vs previous 3 months

        // Order frequency trends
        Double orderFrequency3m,                       // Orders per week in last 3 months
        Double orderFrequencyTrend3m,                  // % change vs previous 3 months
        Integer consecutiveMissedOrders,               // Weeks without orders (current streak)

        // Credit utilization trends
        Double creditUtilization3m,                    // Average credit utilization in last 3 months
        Double creditUtilizationTrajectory,            // Linear regression slope of utilization over 3 months
        Double peakUtilization3m,                      // Maximum utilization reached in 3 months
        Boolean hitCreditLimit3m,                      // true if utilization reached 95%+ in last 3 months

        // Partial payment trends
        Double partialPaymentFreq3m,                   // % of payments that were partial in last 3 months
        Double partialPaymentFreqTrend3m,              // Change vs previous 3 months
        Integer consecutivePartialPayments,            // Current streak of partial payments

        // Order value trends
        Double avgOrderValue3m,                        // Average order value in last 3 months
        Double avgOrderValueTrend3m,                   // % change vs previous 3 months
        Double orderValueVolatility3m,                 // Std deviation of order values (instability indicator)

        // Overall financial health indicators
        BigDecimal totalOutstanding,                   // Current total unpaid amount
        Double outstandingTrend3m,                     // % change in outstanding balance over 3 months
        Integer daysOverdueMax,                        // Maximum days overdue across all outstanding invoices
        Double paymentToOrderRatio3m                   // Total payments / total orders in 3 months (should be ~1.0)
) {
}
