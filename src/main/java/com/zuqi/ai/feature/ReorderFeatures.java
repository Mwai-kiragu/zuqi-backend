package com.zuqi.ai.feature;

import java.util.UUID;

/**
 * Feature vector for reorder optimization calculations.
 * Used by ReorderOptimizationService to compute EOQ and safety stock.
 */
public record ReorderFeatures(
        UUID distributorId,
        UUID warehouseId,
        UUID productId,
        double avgDailyDemand,          // Units sold per day (30-day average)
        double demandVariabilityCv,     // Coefficient of variation of daily demand
        double supplierLeadTimeAvgDays, // Average days from PO to receipt
        double supplierLeadTimeStddev,  // Standard deviation of lead time
        double carryingCostRate,        // Annual carrying cost as fraction of unit cost (default 0.25)
        double orderingCostFixed,       // Fixed cost per order placement (KES, default 500)
        double stockoutCostPerUnit,     // Lost margin per unit stockout (KES)
        double currentStockLevel,       // Current units on hand
        double pendingOrdersQty,        // Units on open purchase orders
        double daysOfSupplyRemaining,   // currentStockLevel / avgDailyDemand
        double unitCostKes              // Average unit cost (KES), used for EOQ carrying cost
) {}
