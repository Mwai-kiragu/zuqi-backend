package com.zuqi.ai.prediction;

import com.zuqi.ai.feature.InventoryFeatureService;
import com.zuqi.ai.feature.InventoryFeatures;
import com.zuqi.ai.model.ModelLoaderService;
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

    static final String MODEL_NAME = "stockout_predictor";

    private final ModelLoaderService      modelLoader;
    private final InventoryFeatureService inventoryFeatureService;
    private final StockoutFeatureBuilder  featureBuilder;

    /**
     * Predict stockout probability for a warehouse-product pair.
     */
    public StockoutResult predict(UUID warehouseId, UUID productId) {
        try {
            Model<Label> model = modelLoader.loadModel(MODEL_NAME);
            if (model == null) {
                log.warn("No active model for {}, returning safe default", MODEL_NAME);
                return defaultResult(warehouseId, productId);
            }

            InventoryFeatures features = inventoryFeatureService.computeFeatures(warehouseId, productId);
            ArrayExample<Label> example = featureBuilder.buildExample(features);

            Prediction<Label> prediction = model.predict(example);

            // Extract STOCKOUT probability from output scores
            Map<String, Label> scores = prediction.getOutputScores();
            Label stockoutLabel = scores.get(StockoutFeatureBuilder.LABEL_STOCKOUT);
            double stockoutProb = stockoutLabel != null ? stockoutLabel.getScore() : 0.0;

            String predLabel = prediction.getOutput().getLabel();
            double daysRemaining = featureBuilder.computeDaysOfStockRemaining(features);

            log.debug("Stockout prediction: warehouse={} product={} prob={} days={}",
                    warehouseId, productId,
                    String.format("%.3f", stockoutProb),
                    String.format("%.1f", daysRemaining));

            return StockoutResult.builder()
                    .warehouseId(warehouseId)
                    .productId(productId)
                    .stockoutProbability(stockoutProb)
                    .prediction(predLabel)
                    .daysOfStockRemaining(daysRemaining)
                    .modelVersion(MODEL_NAME)
                    .build();

        } catch (Exception e) {
            log.error("Stockout prediction failed for warehouse={} product={}: {}",
                    warehouseId, productId, e.getMessage(), e);
            return defaultResult(warehouseId, productId);
        }
    }

    StockoutResult defaultResult(UUID warehouseId, UUID productId) {
        return StockoutResult.builder()
                .warehouseId(warehouseId)
                .productId(productId)
                .stockoutProbability(0.0)
                .prediction(StockoutFeatureBuilder.LABEL_NO_STOCKOUT)
                .daysOfStockRemaining(30.0)
                .modelVersion("fallback")
                .build();
    }

    // ── Result record ─────────────────────────────────────────────────────

    @Builder
    public record StockoutResult(
            UUID   warehouseId,
            UUID   productId,
            double stockoutProbability,
            String prediction,
            double daysOfStockRemaining,
            String modelVersion
    ) {}
}
