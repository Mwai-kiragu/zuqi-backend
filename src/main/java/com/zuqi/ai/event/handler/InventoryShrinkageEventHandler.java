package com.zuqi.ai.event.handler;

import com.zuqi.ai.event.StockAdjustedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Event handler for inventory-related AI operations.
 *
 * Triggered by StockAdjustedEvent to perform:
 * - Inventory shrinkage detection (Isolation Forest)
 * - Stockout prediction updates
 * - Demand forecasting adjustments
 *
 * Blueprint reference: plan.md Section 7.5 - Inventory Shrinkage Detection
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryShrinkageEventHandler {

    // TODO: Inject ShrinkageDetectionService when implemented in Phase 4
    // TODO: Inject StockoutPredictionService when implemented in Phase 4

    @Async
    @EventListener
    public void handleStockAdjusted(StockAdjustedEvent event) {
        log.info("Received StockAdjustedEvent for stock {} (warehouse: {}, product: {}, adjustment: {})",
                event.stockId(), event.warehouseId(), event.productId(), event.adjustmentAmount());

        try {
            // TODO Phase 4: Implement shrinkage detection
            // shrinkageDetectionService.detectShrinkage(event);

            // TODO Phase 4: Update stockout predictions
            // stockoutPredictionService.updatePredictions(event.warehouseId(), event.productId());

            log.debug("Inventory AI processing completed for stock {}", event.stockId());
        } catch (Exception e) {
            log.error("Failed to process inventory AI operations for stock {}", event.stockId(), e);
            // Don't rethrow - event processing failures should not break the transaction
        }
    }
}
