package com.zuqi.ai.feature;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Feature vector for expiry risk prediction.
 * Target: sell_through_probability (probability batch sells before expiry).
 */
public record ExpiryFeatures(
        UUID distributorId,
        UUID warehouseId,
        UUID productId,
        String batchNumber,
        LocalDate expiryDate,
        int daysToExpiry,                  // Days until expiry date
        double currentStockQty,            // Units remaining in batch
        double avgDailySalesRate,          // Units sold per day (30-day history)
        double projectedDaysToSell,        // currentStockQty / avgDailySalesRate
        double similarSkuVelocity,         // Avg daily sales of similar SKUs (same category)
        double warehouseTurnoverRate,      // Warehouse-level inventory turnover (annualised)
        double priceSensitivityScore,      // 0.0–1.0: how price-elastic this SKU is
        double batchAgeRatio               // (total_shelf_life - days_to_expiry) / total_shelf_life
) {}
