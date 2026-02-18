package com.zuqi.ai.event.handler;

import com.zuqi.ai.anomaly.AlertService;
import com.zuqi.ai.anomaly.ShrinkageDetector;
import com.zuqi.ai.event.StockAdjustedEvent;
import com.zuqi.domain.ai.AlertSeverity;
import com.zuqi.domain.ai.AlertType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Event handler for inventory-related AI operations.
 *
 * Triggered by StockAdjustedEvent to run real-time shrinkage detection.
 *
 * Blueprint reference: plan.md Section 6.3 - ShrinkageDetector
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryShrinkageEventHandler {

    private final ShrinkageDetector shrinkageDetector;
    private final AlertService      alertService;

    @Async
    @EventListener
    public void handleStockAdjusted(StockAdjustedEvent event) {
        log.debug("StockAdjustedEvent: stock={} warehouse={} product={} adjustment={}",
                event.stockId(), event.warehouseId(), event.productId(), event.adjustmentAmount());

        try {
            ShrinkageDetector.ShrinkageResult result =
                    shrinkageDetector.detect(event.warehouseId(), event.productId());

            if (result.isAnomaly()) {
                AlertSeverity severity = result.anomalyScore() > 0.7 ? AlertSeverity.CRITICAL
                        : result.anomalyScore() > 0.5 ? AlertSeverity.HIGH
                        : AlertSeverity.MEDIUM;

                Map<String, Object> context = Map.of(
                        "stockId",          event.stockId().toString(),
                        "warehouseId",      event.warehouseId().toString(),
                        "productId",        event.productId().toString(),
                        "movementType",     event.movementType() != null ? event.movementType() : "UNKNOWN",
                        "adjustmentAmount", String.valueOf(event.adjustmentAmount()),
                        "anomalyScore",     result.anomalyScore(),
                        "modelVersion",     result.modelVersion()
                );

                alertService.createAlert(
                        AlertType.SHRINKAGE,
                        severity,
                        "STOCK",
                        event.stockId(),
                        event.distributorId(),
                        result.anomalyScore(),
                        "Inventory shrinkage detected: warehouse=" + event.warehouseId()
                                + " product=" + event.productId(),
                        context
                );

                log.info("Shrinkage alert raised for stock={} warehouse={} product={} score={}",
                        event.stockId(), event.warehouseId(), event.productId(),
                        String.format("%.3f", result.anomalyScore()));
            }
        } catch (Exception e) {
            log.error("Failed to process StockAdjustedEvent stock={}: {}", event.stockId(), e.getMessage(), e);
        }
    }
}
