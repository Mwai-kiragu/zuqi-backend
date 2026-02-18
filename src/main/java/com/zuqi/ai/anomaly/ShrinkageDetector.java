package com.zuqi.ai.anomaly;

import com.zuqi.ai.feature.InventoryFeatureService;
import com.zuqi.ai.feature.InventoryFeatures;
import com.zuqi.ai.model.ModelLoaderService;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.tribuo.Model;
import org.tribuo.Prediction;
import org.tribuo.anomaly.Event;
import org.tribuo.impl.ArrayExample;

import java.util.UUID;

/**
 * Detects inventory shrinkage using a trained LibSVM one-class anomaly model.
 *
 * Blueprint reference: plan.md Section 6.3 - ShrinkageDetector
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ShrinkageDetector {

    static final String MODEL_NAME = "shrinkage_detector";

    private final ModelLoaderService modelLoader;
    private final InventoryFeatureService inventoryFeatureService;
    private final AnomalyFeatureBuilder anomalyFeatureBuilder;

    /**
     * Detect inventory shrinkage for a warehouse-product pair.
     *
     * @param warehouseId Warehouse to check
     * @param productId   Product/SKU to check
     * @return ShrinkageResult with anomaly flag and score
     */
    public ShrinkageResult detect(UUID warehouseId, UUID productId) {
        try {
            // 1. Load active model
            Model<Event> model = modelLoader.loadModel(MODEL_NAME);
            if (model == null) {
                log.warn("No active model found for {}, returning safe default", MODEL_NAME);
                return defaultResult(warehouseId, productId);
            }

            // 2. Compute inventory features
            InventoryFeatures features = inventoryFeatureService.computeFeatures(warehouseId, productId);

            // 3. Build Tribuo example (labelled EXPECTED — label not used during inference)
            ArrayExample<Event> example = anomalyFeatureBuilder.buildInventoryExample(features);

            // 4. Predict
            Prediction<Event> prediction = model.predict(example);

            // 5. Extract anomaly type and score
            boolean isAnomaly = prediction.getOutput().getType() == Event.EventType.ANOMALOUS;

            // LibSVM decision function: negative = anomalous; normalise to [0,1]
            double rawScore  = prediction.getOutput().getScore();
            double anomalyScore = normaliseScore(rawScore);

            log.debug("Shrinkage check: warehouse={} product={} anomaly={} score={}",
                    warehouseId, productId, isAnomaly, String.format("%.3f", anomalyScore));

            return ShrinkageResult.builder()
                    .warehouseId(warehouseId)
                    .productId(productId)
                    .isAnomaly(isAnomaly)
                    .anomalyScore(anomalyScore)
                    .features(features)
                    .modelVersion(MODEL_NAME)
                    .build();

        } catch (Exception e) {
            log.error("Shrinkage detection failed for warehouse={} product={}: {}",
                    warehouseId, productId, e.getMessage(), e);
            return defaultResult(warehouseId, productId);
        }
    }

    /** Safe fallback when the model is unavailable. */
    ShrinkageResult defaultResult(UUID warehouseId, UUID productId) {
        return ShrinkageResult.builder()
                .warehouseId(warehouseId)
                .productId(productId)
                .isAnomaly(false)
                .anomalyScore(0.0)
                .modelVersion("fallback")
                .build();
    }

    /**
     * Normalise LibSVM decision score to [0,1] where 1 = most anomalous.
     * LibSVM one-class: positive = inlier, negative = outlier.
     */
    private double normaliseScore(double rawScore) {
        double clamped = Math.max(-5.0, Math.min(5.0, rawScore));
        return 1.0 / (1.0 + Math.exp(clamped));  // inverted sigmoid
    }

    // ── Result record ─────────────────────────────────────────────────────

    @Builder
    public record ShrinkageResult(
            UUID warehouseId,
            UUID productId,
            boolean isAnomaly,
            double anomalyScore,
            InventoryFeatures features,
            String modelVersion
    ) {}
}
