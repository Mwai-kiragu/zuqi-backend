package com.zuqi.ai.feature;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Sales rep performance features for underperformance detection.
 * Used by sales rep performance prediction models (XGBoost regression).
 *
 * Features capture:
 * - Visit and conversion metrics
 * - Order value and volume metrics
 * - Merchant acquisition and retention
 * - Collection and payment performance
 * - Route adherence and territory coverage
 *
 * Blueprint reference: plan.md Section 4.2 - SalesRepFeatureService
 */
@Builder
public record SalesRepFeatures(
        UUID salesRepId,
        LocalDateTime periodStart,
        LocalDateTime periodEnd,
        LocalDateTime computedAt,

        // Visit and conversion metrics
        Integer visitCount,                            // Total visits made in period
        Integer visitTarget,                           // Target visits for period
        Double visitCountVsTarget,                     // (visitCount / visitTarget) * 100
        Integer ordersCreated,                         // Number of orders created
        Double orderConversionRate,                    // (ordersCreated / visitCount) * 100

        // Order value metrics
        BigDecimal totalOrderValue,                    // Sum of all order values
        BigDecimal avgOrderValue,                      // Average value per order

        // Merchant metrics
        Integer newMerchantsAcquired,                  // New merchants onboarded in period
        Integer activeMerchants,                       // Total active merchants assigned
        Double merchantRetentionRate,                  // % of merchants who placed orders

        // Collection and payment metrics
        BigDecimal collectionsTarget,                  // Target collections for period
        BigDecimal collectionsActual,                  // Actual collections made
        Double collectionRate,                         // (collectionsActual / collectionsTarget) * 100
        Integer paymentsCollected,                     // Number of payments collected

        // Route and territory metrics
        Integer routeVisitsPlanned,                    // Planned visits per route schedule
        Integer routeVisitsCompleted,                  // Actual visits completed
        Double routeAdherencePct,                      // (routeVisitsCompleted / routeVisitsPlanned) * 100
        Integer assignedTerritoryMerchants,            // Total merchants in assigned territory
        Integer visitedTerritoryMerchants,             // Unique merchants visited in period
        Double territoryPenetrationPct                 // (visitedTerritoryMerchants / assignedTerritoryMerchants) * 100
) {
}
