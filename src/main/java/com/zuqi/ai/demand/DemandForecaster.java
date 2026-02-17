package com.zuqi.ai.demand;

import com.zuqi.ai.feature.DemandFeatures;
import com.zuqi.ai.feature.OrderFeatureService;
import com.zuqi.ai.model.ModelLoaderService;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.tribuo.Example;
import org.tribuo.Model;
import org.tribuo.Prediction;
import org.tribuo.regression.Regressor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * XGBoost regressor for demand forecasting.
 *
 * Predicts order quantity for a merchant-SKU combination for next week.
 * Trained on historical order data, retrained weekly.
 *
 * Blueprint: plan.md Section 6.2 - Demand Forecasting Module
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DemandForecaster {

    private final ModelLoaderService modelLoader;
    private final OrderFeatureService orderFeatureService;
    private final DemandFeatureBuilder featureBuilder;

    private static final String MODEL_NAME = "demand_forecaster";
    private static final BigDecimal MIN_QUANTITY = BigDecimal.ZERO;
    private static final BigDecimal MAX_QUANTITY = BigDecimal.valueOf(10_000); // Max 10k units per order

    /**
     * Forecast demand for a merchant-SKU combination.
     *
     * @param merchantId Merchant ID
     * @param productId Product/SKU ID
     * @return Demand forecast with predicted quantity and confidence
     */
    public DemandForecast forecastDemand(UUID merchantId, UUID productId) {
        try {
            // 1. Load active model
            Model<Regressor> model = modelLoader.loadModel(MODEL_NAME);
            if (model == null) {
                log.warn("No active model found for {}, returning default forecast", MODEL_NAME);
                return defaultForecast(merchantId, productId);
            }

            // 2. Compute demand features
            DemandFeatures features = orderFeatureService.computeFeatures(merchantId, productId);

            // 3. Build ML feature vector (use dummy target for inference)
            Example<Regressor> example = featureBuilder.buildRegressionExample(
                    features,
                    BigDecimal.ZERO  // Target not used during inference
            );

            // 4. Predict
            Prediction<Regressor> prediction = model.predict(example);

            // 5. Extract predicted value
            double predictedQty = prediction.getOutput().getValues()[0];

            // 6. Apply constraints and rounding
            BigDecimal forecastedQuantity = BigDecimal.valueOf(predictedQty)
                    .max(MIN_QUANTITY)    // Floor at 0
                    .min(MAX_QUANTITY)    // Cap at 10k
                    .setScale(0, RoundingMode.HALF_UP);  // Round to whole units

            // 7. Calculate confidence (based on prediction variance if available)
            double confidence = calculateConfidence(prediction, features);

            log.debug("Demand forecast for merchant {} SKU {}: {} units (confidence {:.2f})",
                    merchantId, productId, forecastedQuantity, confidence);

            return DemandForecast.builder()
                    .merchantId(merchantId)
                    .productId(productId)
                    .predictedQuantity(forecastedQuantity)
                    .confidence(confidence)
                    .rollingAvg4w(features.rollingAvg4w())
                    .rollingAvg12w(features.rollingAvg12w())
                    .trendDirection(features.trendDirection())
                    .modelVersion(MODEL_NAME + "-v" + getModelVersion(model))
                    .build();

        } catch (Exception e) {
            log.error("Demand forecasting failed for merchant {} SKU {}: {}",
                    merchantId, productId, e.getMessage(), e);
            return defaultForecast(merchantId, productId);
        }
    }

    /**
     * Batch forecast demand for multiple merchant-SKU combinations.
     *
     * @param merchantProductPairs List of (merchantId, productId) pairs
     * @return List of demand forecasts
     */
    public java.util.List<DemandForecast> batchForecast(
            java.util.List<MerchantProductPair> merchantProductPairs) {

        log.info("Batch forecasting demand for {} merchant-SKU pairs", merchantProductPairs.size());

        return merchantProductPairs.stream()
                .map(pair -> forecastDemand(pair.merchantId(), pair.productId()))
                .toList();
    }

    /**
     * Default forecast when model is unavailable or prediction fails.
     *
     * Falls back to rolling average if available.
     */
    private DemandForecast defaultForecast(UUID merchantId, UUID productId) {
        try {
            DemandFeatures features = orderFeatureService.computeFeatures(merchantId, productId);

            // Use 4-week rolling average as fallback
            BigDecimal fallbackQty = features.rollingAvg4w() != null ?
                    features.rollingAvg4w() : BigDecimal.ZERO;

            return DemandForecast.builder()
                    .merchantId(merchantId)
                    .productId(productId)
                    .predictedQuantity(fallbackQty)
                    .confidence(0.5) // Low confidence for fallback
                    .rollingAvg4w(features.rollingAvg4w())
                    .rollingAvg12w(features.rollingAvg12w())
                    .trendDirection(features.trendDirection())
                    .modelVersion("fallback-avg")
                    .build();

        } catch (Exception e) {
            log.error("Even default forecast failed for merchant {} SKU {}: {}",
                    merchantId, productId, e.getMessage());

            return DemandForecast.builder()
                    .merchantId(merchantId)
                    .productId(productId)
                    .predictedQuantity(BigDecimal.ZERO)
                    .confidence(0.0)
                    .rollingAvg4w(BigDecimal.ZERO)
                    .rollingAvg12w(BigDecimal.ZERO)
                    .trendDirection("STABLE")
                    .modelVersion("error")
                    .build();
        }
    }

    /**
     * Calculate prediction confidence.
     *
     * Higher confidence when:
     * - Historical data is stable (low variance)
     * - Recent purchases are consistent
     * - Model has seen similar patterns
     */
    private double calculateConfidence(Prediction<Regressor> prediction, DemandFeatures features) {
        double baseConfidence = 0.7; // Default confidence

        // Reduce confidence if no historical data
        if (features.rollingAvg4w() == null || features.rollingAvg4w().compareTo(BigDecimal.ZERO) == 0) {
            baseConfidence -= 0.3;
        }

        // Increase confidence for stable trends
        if ("STABLE".equals(features.trendDirection())) {
            baseConfidence += 0.1;
        }

        // Reduce confidence for highly volatile trends
        if ("DECREASING".equals(features.trendDirection()) || "INCREASING".equals(features.trendDirection())) {
            baseConfidence -= 0.05;
        }

        // Reduce confidence for new merchants (< 90 days tenure)
        if (features.merchantTenureDays() < 90) {
            baseConfidence -= 0.15;
        }

        // Clamp to [0.0, 1.0]
        return Math.max(0.0, Math.min(1.0, baseConfidence));
    }

    /**
     * Get model version from metadata.
     */
    private int getModelVersion(Model<Regressor> model) {
        // Extract from model metadata/provenance
        // For now, return 1
        return 1;
    }

    /**
     * Demand forecast result.
     */
    @Builder
    public record DemandForecast(
            UUID merchantId,
            UUID productId,
            BigDecimal predictedQuantity,     // Predicted quantity for next week
            double confidence,                 // 0.0-1.0 (prediction confidence)
            BigDecimal rollingAvg4w,          // Historical context
            BigDecimal rollingAvg12w,         // Historical context
            String trendDirection,             // Trend indicator
            String modelVersion                // Model version used
    ) {
    }

    /**
     * Merchant-Product pair for batch forecasting.
     */
    public record MerchantProductPair(UUID merchantId, UUID productId) {
    }
}
