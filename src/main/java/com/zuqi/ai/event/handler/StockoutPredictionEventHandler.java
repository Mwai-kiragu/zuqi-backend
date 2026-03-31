package com.zuqi.ai.event.handler;

import com.zuqi.ai.event.StockAdjustedEvent;
import com.zuqi.ai.prediction.PredictionAlertService;
import com.zuqi.ai.prediction.StockoutPredictor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Event handler that runs stockout risk prediction whenever stock is adjusted.
 *
 * <p>Complements {@link InventoryShrinkageEventHandler} (same trigger, different model):
 * shrinkage detection catches unrecorded losses; stockout prediction flags low-stock risk
 * so re-orders can be raised before a product runs out.
 *
 * <p>Runs asynchronously so it never blocks the stock-adjustment transaction.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StockoutPredictionEventHandler {

    private final StockoutPredictor     stockoutPredictor;
    private final PredictionAlertService predictionAlertService;

    @Async
    @EventListener
    public void handleStockAdjusted(StockAdjustedEvent event) {
        log.debug("StockAdjustedEvent → stockout check: warehouse={} product={} movement={}",
                event.warehouseId(), event.productId(), event.movementType());

        try {
            StockoutPredictor.StockoutResult result =
                    stockoutPredictor.predict(event.warehouseId(), event.productId());

            predictionAlertService.evaluateStockoutAndAlert(result, event.distributorId());

        } catch (Exception e) {
            log.error("Stockout prediction failed for warehouse={} product={}: {}",
                    event.warehouseId(), event.productId(), e.getMessage(), e);
        }
    }
}
