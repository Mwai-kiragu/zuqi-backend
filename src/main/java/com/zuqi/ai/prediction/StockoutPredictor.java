package com.zuqi.ai.prediction;

import com.zuqi.ai.feature.InventoryFeatureService;
import com.zuqi.ai.feature.InventoryFeatures;
import com.zuqi.ai.model.ModelLoaderService;
import com.zuqi.ai.model.ModelPhaseService;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.tribuo.Model;
import org.tribuo.Prediction;
import org.tribuo.classification.Label;
import org.tribuo.impl.ArrayExample;

import java.util.Map;
import java.util.UUID;

/**
 * XGBoost classifier that predicts the probability of a product stocking out.
 *
 * Blueprint reference: implementation_plan.md Phase 4, Step 7a
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StockoutPredictor {

    public static final String MODEL_NAME = "stockout_predictor";

    private final ModelLoaderService      modelLoader;
    private final InventoryFeatureService inventoryFeatureService;
    private final StockoutFeatureBuilder  featureBuilder;
    private final ModelPhaseService       phaseService;

    /**
     * Predict stockout probability for a warehouse-product pair.
     */
    public StockoutResult predict(UUID warehouseId, UUID productId) {
        try {
            Model<Label> model = modelLoader.loadModel(MODEL_NAME);
            InventoryFeatures features = inventoryFeatureService.computeFeatures(warehouseId, productId);

            double stockoutProb;
            String predLabel;

            if (model == null) {
                log.warn("No active model for {}, returning safe default", MODEL_NAME);
                stockoutProb = 0.0;
                predLabel = StockoutFeatureBuilder.LABEL_NO_STOCKOUT;
            } else {
                ArrayExample<Label> example = featureBuilder.buildExample(features);
                Prediction<Label> prediction = model.predict(example);
                Map<String, Label> scores = prediction.getOutputScores();
                Label stockoutLabel = scores.get(StockoutFeatureBuilder.LABEL_STOCKOUT);
                stockoutProb = phaseService.applyModifier(
                        stockoutLabel != null ? stockoutLabel.getScore() : 0.0, MODEL_NAME);
                predLabel = prediction.getOutput().getLabel();
            }

            double daysRemaining = featureBuilder.computeDaysOfStockRemaining(features);
            double demand7d = features.predictedDemand7d() != null
                    ? features.predictedDemand7d().doubleValue()
                    : (features.consumptionRate7d() != null ? features.consumptionRate7d().doubleValue() : 0.0);
            String consumptionTrend = features.consumptionTrend() != null ? features.consumptionTrend() : "STABLE";
            double trendPct = computeTrendPct(features);
            double currentStock = features.currentStock() != null ? features.currentStock().doubleValue() : 0.0;

            log.debug("Stockout prediction: warehouse={} product={} prob={} days={}",
                    warehouseId, productId,
                    String.format("%.3f", stockoutProb),
                    String.format("%.1f", daysRemaining));

            return StockoutResult.builder()
                    .warehouseId(warehouseId)
                    .productId(productId)
                    .riskScore(stockoutProb)
                    .prediction(predLabel)
                    .daysUntilStockout(daysRemaining)
                    .currentStock(currentStock)
                    .demand7d(demand7d)
                    .consumptionTrend(consumptionTrend)
                    .trendPct(trendPct)
                    .modelVersion(MODEL_NAME)
                    .build();

        } catch (Exception e) {
            log.error("Stockout prediction failed for warehouse={} product={}: {}",
                    warehouseId, productId, e.getMessage(), e);
            return defaultResult(warehouseId, productId);
        } catch (Error e) {
            log.error("Fatal error in stockout prediction for warehouse={} product={} (native library issue?): {}",
                    warehouseId, productId, e.getMessage(), e);
            return defaultResult(warehouseId, productId);
        }
    }

    StockoutResult defaultResult(UUID warehouseId, UUID productId) {
        return StockoutResult.builder()
                .warehouseId(warehouseId)
                .productId(productId)
                .riskScore(0.0)
                .prediction(StockoutFeatureBuilder.LABEL_NO_STOCKOUT)
                .daysUntilStockout(30.0)
                .currentStock(0.0)
                .demand7d(0.0)
                .consumptionTrend("STABLE")
                .trendPct(0.0)
                .modelVersion("fallback")
                .build();
    }

    private double computeTrendPct(InventoryFeatures features) {
        if (features.consumptionRate7d() == null || features.consumptionRate30d() == null) return 0.0;
        double rate7d  = features.consumptionRate7d().doubleValue();
        double rate30d = features.consumptionRate30d().doubleValue();
        if (rate30d <= 0) return 0.0;
        return ((rate7d - rate30d) / rate30d) * 100.0;
    }

    // ── Result record ─────────────────────────────────────────────────────

    @Builder
    public record StockoutResult(
            UUID   warehouseId,
            UUID   productId,
            double riskScore,
            String prediction,
            double daysUntilStockout,
            double currentStock,
            double demand7d,
            String consumptionTrend,
            double trendPct,
            String modelVersion
    ) {}
}
