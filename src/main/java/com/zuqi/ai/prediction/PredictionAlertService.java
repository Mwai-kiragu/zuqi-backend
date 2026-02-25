package com.zuqi.ai.prediction;

import com.zuqi.ai.anomaly.AlertService;
import com.zuqi.domain.ai.AlertSeverity;
import com.zuqi.domain.ai.AlertType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * Evaluates stockout and rep-performance predictions and raises alerts when thresholds are crossed.
 *
 * Blueprint reference: implementation_plan.md Phase 4, Step 6
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PredictionAlertService {

    private final AlertService alertService;

    @Value("${zuqi.ai.prediction.stockout-alert-threshold:0.5}")
    private double stockoutAlertThreshold;

    @Value("${zuqi.ai.prediction.rep-performance-alert-threshold:60.0}")
    private double repPerformanceAlertThreshold;

    /**
     * Evaluate a stockout prediction and raise STOCKOUT_RISK alert if probability exceeds threshold.
     */
    public void evaluateStockoutAndAlert(StockoutPredictor.StockoutResult result, UUID distributorId) {
        double prob = result.stockoutProbability();

        if (prob < stockoutAlertThreshold) {
            return;
        }

        AlertSeverity severity = prob >= 0.7 ? AlertSeverity.HIGH : AlertSeverity.MEDIUM;

        Map<String, Object> context = Map.of(
                "warehouseId",         result.warehouseId().toString(),
                "productId",           result.productId().toString(),
                "stockoutProbability", prob,
                "daysOfStockRemaining", result.daysOfStockRemaining(),
                "prediction",          result.prediction(),
                "modelVersion",        result.modelVersion()
        );

        alertService.createAlert(
                AlertType.STOCKOUT_RISK,
                severity,
                "PRODUCT",
                result.productId(),
                distributorId,
                prob,
                "Stockout risk detected: warehouse=" + result.warehouseId()
                        + " product=" + result.productId()
                        + " probability=" + String.format("%.2f", prob),
                context
        );

        log.info("STOCKOUT_RISK alert: warehouse={} product={} prob={}",
                result.warehouseId(), result.productId(), String.format("%.3f", prob));
    }

    /**
     * Evaluate a rep-performance prediction and raise REP_UNDERPERFORMANCE alert if score is below threshold.
     */
    public void evaluateRepPerformanceAndAlert(RepPerformancePredictor.RepPerformanceResult result,
                                               UUID distributorId) {
        double score = result.performanceScore();

        if (score >= repPerformanceAlertThreshold) {
            return;
        }

        AlertSeverity severity = score < 40.0 ? AlertSeverity.HIGH : AlertSeverity.MEDIUM;

        Map<String, Object> context = Map.of(
                "salesRepId",      result.salesRepId().toString(),
                "performanceScore", score,
                "performanceTier",  result.performanceTier(),
                "modelVersion",     result.modelVersion()
        );

        alertService.createAlert(
                AlertType.REP_UNDERPERFORMANCE,
                severity,
                "SALES_REP",
                result.salesRepId(),
                distributorId,
                (100.0 - score) / 100.0,   // invert: low score → high anomaly score
                "Sales rep underperformance detected: rep=" + result.salesRepId()
                        + " score=" + String.format("%.1f", score)
                        + " tier=" + result.performanceTier(),
                context
        );

        log.info("REP_UNDERPERFORMANCE alert: rep={} score={} tier={}",
                result.salesRepId(), String.format("%.1f", score), result.performanceTier());
    }
}
