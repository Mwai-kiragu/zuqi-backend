package com.zuqi.ai.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Event published when stock is adjusted (added, removed, or transferred).
 *
 * Triggers AI operations:
 * - Inventory shrinkage detection (Isolation Forest)
 * - Stockout prediction model retraining
 * - Demand forecasting updates
 *
 * Blueprint reference: plan.md Section 5 - Event-Driven AI Integration
 */
public record StockAdjustedEvent(
        UUID stockId,
        UUID warehouseId,
        UUID productId,
        UUID distributorId,
        BigDecimal previousQuantity,
        BigDecimal newQuantity,
        BigDecimal adjustmentAmount,
        String movementType, // INBOUND, OUTBOUND, TRANSFER, ADJUSTMENT
        String reason,
        LocalDateTime adjustedAt
) {
    public StockAdjustedEvent {
        if (stockId == null) {
            throw new IllegalArgumentException("stockId cannot be null");
        }
        if (warehouseId == null) {
            throw new IllegalArgumentException("warehouseId cannot be null");
        }
        if (productId == null) {
            throw new IllegalArgumentException("productId cannot be null");
        }
        if (distributorId == null) {
            throw new IllegalArgumentException("distributorId cannot be null");
        }
    }
}
