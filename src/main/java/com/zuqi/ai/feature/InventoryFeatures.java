package com.zuqi.ai.feature;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Inventory features for shrinkage detection and stockout prediction.
 * Used by anomaly detection models (Isolation Forest) and stockout prediction (XGBoost).
 *
 * Features capture:
 * - Stock level discrepancies (actual vs expected)
 * - Manual adjustment patterns (frequency, timing, users)
 * - Consumption patterns and trends
 * - Pending and expected quantities
 *
 * Blueprint reference: plan.md Section 4.2 - InventoryFeatureService
 */
@Builder
public record InventoryFeatures(
        UUID warehouseId,
        UUID productId,
        LocalDateTime computedAt,

        // Stock level features
        BigDecimal currentStock,                       // Current quantity in stock
        BigDecimal expectedStock,                      // Expected stock based on movements
        BigDecimal discrepancy,                        // currentStock - expectedStock (negative = shrinkage)
        Double discrepancyPct,                         // (discrepancy / expectedStock) * 100

        // Manual adjustment features
        Integer manualAdjustmentCount7d,               // Number of manual adjustments in last 7 days
        Map<String, Integer> adjustmentTimeDistribution, // Hour of day → count (for pattern detection)
        List<UUID> adjustingUserIds,                   // Unique user IDs who made adjustments in last 7 days

        // Consumption rate features
        BigDecimal consumptionRate7d,                  // Average daily consumption (last 7 days)
        BigDecimal consumptionRate30d,                 // Average daily consumption (last 30 days)
        String consumptionTrend,                       // "INCREASING", "DECREASING", "STABLE"

        // Pending quantities
        BigDecimal pendingReservedQty,                 // Quantity reserved for pending orders
        BigDecimal expectedIncomingQty                 // Quantity expected from pending purchases
) {
}
