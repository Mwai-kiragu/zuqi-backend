package com.zuqi.ai.crm;

import java.util.UUID;

/**
 * Immutable feature record for all CRM AI models (segmentation, CLV, churn, visit optimisation).
 *
 * <p>Computed by {@link CustomerAnalyticsFeatureServiceImpl} for real customers and by
 * {@link SyntheticCustomerAnalyticsFeatureBuilder} from the synthetic data bundle during training.
 */
public record CustomerAnalyticsFeatures(
        UUID customerId,
        UUID distributorId,

        /** Sum of order amounts (non-CANCELLED) in the last 90 days. */
        double totalRevenue90d,

        /** Sum of all order amounts (lifetime). */
        double lifetimeRevenue,

        /** Revenue last 3 months. */
        double revenue3m,

        /** Revenue last 6 months. */
        double revenue6m,

        /** Revenue last 12 months. */
        double revenue12m,

        /** Orders per week based on last-90-day window (orderCount90d / 13.0). */
        double orderFrequencyPerWeek,

        /** Average order value across all non-CANCELLED orders. */
        double avgOrderValue,

        /** (revenue last 30d − revenue 30–60d ago) / max(revenue 30–60d, 1.0). */
        double revenueTrendSlope,

        /** Percentage of on-time payments: (on-time / total) × 100. 100 if no payments. */
        double paymentTimelinessScore,

        /** Outstanding balance / credit limit × 100. 0 if no credit limit. */
        double creditUtilizationPct,

        /** Days since the most recent non-CANCELLED order. Integer.MAX_VALUE if no orders. */
        int daysSinceLastOrder,

        /** Diversity proxy: min(1.0, orderCount90d / 10.0). 0 if no orders in 90 days. */
        double productDiversityScore,

        /** Months between customer creation and now. */
        int tenureMonths,

        /** Customer category name, or "UNKNOWN" if unset. */
        String customerCategory,

        /** Number of non-CANCELLED orders in the last 30 days. */
        double orderCount30d,

        /** Number of non-CANCELLED orders in the last 90 days. */
        double orderCount90d
) {}
