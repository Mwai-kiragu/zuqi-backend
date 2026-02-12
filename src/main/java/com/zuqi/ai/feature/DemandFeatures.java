package com.zuqi.ai.feature;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Demand forecasting features for merchant-SKU combinations.
 * Used by demand forecasting models (XGBoost) and AI-powered order suggestions.
 *
 * Features capture:
 * - Historical demand patterns (lag features)
 * - Temporal/seasonal patterns (holidays, paydays, etc.)
 * - Merchant context (category, size, credit status)
 * - SKU context (category, price, promotions)
 *
 * Blueprint reference: plan.md Section 4.2 - OrderFeatureService
 */
@Builder
public record DemandFeatures(
        UUID merchantId,
        UUID productId,
        LocalDateTime computedAt,

        // Lag features - historical quantities ordered
        BigDecimal qty1wAgo,                           // Quantity ordered 1 week ago
        BigDecimal qty2wAgo,                           // Quantity ordered 2 weeks ago
        BigDecimal qty3wAgo,                           // Quantity ordered 3 weeks ago
        BigDecimal qty4wAgo,                           // Quantity ordered 4 weeks ago
        BigDecimal rollingAvg4w,                       // 4-week rolling average
        BigDecimal rollingAvg12w,                      // 12-week rolling average
        String trendDirection,                         // "INCREASING", "DECREASING", "STABLE"

        // Temporal features - seasonality and calendar effects
        Integer dayOfWeek,                             // 1-7 (Monday=1)
        Integer weekOfMonth,                           // 1-5
        Integer monthOfYear,                           // 1-12
        Boolean isHoliday,                             // Kenya public holiday
        Boolean isPaydayWeek,                          // Week containing typical payday (28th-5th)
        Boolean isRamadan,                             // During Ramadan period
        Boolean isChristmasSeason,                     // Nov-Dec festive season

        // Merchant context features
        String merchantCategory,                       // Encoded merchant business category
        String merchantSizeTier,                       // "SMALL", "MEDIUM", "LARGE" based on order volume
        String merchantCreditStatus,                   // "GOOD", "MODERATE", "POOR" based on payment behavior
        Integer merchantTenureDays,                    // Days since merchant registration

        // SKU context features
        String productCategory,                        // Product category name
        String priceTier,                              // "LOW", "MEDIUM", "HIGH" based on price percentile
        Boolean isPromotional,                         // Currently on promotion (discount > 0)
        Integer typicalShelfLifeDays                   // Typical shelf life for this product category
) {
}
