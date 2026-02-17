package com.zuqi.ai.credit;

import com.zuqi.ai.feature.MerchantFeatureService;
import com.zuqi.ai.feature.MerchantFeatures;
import com.zuqi.ai.model.ModelLoaderService;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.tribuo.Example;
import org.tribuo.Model;
import org.tribuo.Prediction;
import org.tribuo.classification.Label;

import java.util.Map;
import java.util.UUID;

/**
 * XGBoost binary classifier for credit default prediction.
 *
 * Predicts whether a merchant will default (binary classification).
 * Trained on synthetic data initially, evolves to real data over time.
 *
 * Blueprint: ML_IMPLEMENTATION_PLAN.md Task 3
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CreditClassifier {

    private final ModelLoaderService modelLoader;
    private final MerchantFeatureService merchantFeatureService;
    private final CreditMlFeatureBuilder featureBuilder;

    private static final String MODEL_NAME = "credit_classifier";

    /**
     * Predict credit default probability for a merchant.
     *
     * @param merchantId Merchant to evaluate
     * @return Credit classification result with score, probability, confidence
     */
    public CreditClassifierResult predict(UUID merchantId) {
        try {
            // 1. Load active model
            Model<Label> model = modelLoader.loadModel(MODEL_NAME);
            if (model == null) {
                log.warn("No active model found for {}, returning default result", MODEL_NAME);
                return defaultResult();
            }

            // 2. Compute merchant features
            MerchantFeatures features = merchantFeatureService.computeFeatures(merchantId);

            // 3. Build ML feature vector
            Example<Label> example = featureBuilder.buildClassificationExample(features, false);

            // 4. Predict
            Prediction<Label> prediction = model.predict(example);

            // 5. Extract probabilities from Label output scores
            Map<String, Label> outputScores = prediction.getOutputScores();
            double defaultProb = outputScores.containsKey("DEFAULT") ?
                    outputScores.get("DEFAULT").getScore() : 0.0;
            double noDefaultProb = outputScores.containsKey("NO_DEFAULT") ?
                    outputScores.get("NO_DEFAULT").getScore() : 0.0;

            // 6. Convert to credit score (0-100, inverted from default probability)
            int creditScore = (int) Math.round((1.0 - defaultProb) * 100);

            // 7. Extract feature importance (if available)
            Map<String, Double> featureImportance = extractFeatureImportance(model);

            log.debug("ML credit prediction for merchant {}: score={}, defaultProb={:.2f}",
                    merchantId, creditScore, defaultProb);

            return CreditClassifierResult.builder()
                    .creditScore(creditScore)
                    .defaultProbability(defaultProb)
                    .noDefaultProbability(noDefaultProb)
                    .confidence(Math.max(defaultProb, noDefaultProb)) // Confidence = max probability
                    .prediction(prediction.getOutput().getLabel())
                    .featureImportance(featureImportance)
                    .modelVersion(MODEL_NAME + "-v" + getModelVersion(model))
                    .build();

        } catch (Exception e) {
            log.error("Credit classification failed for merchant {}: {}", merchantId, e.getMessage(), e);
            return defaultResult();
        }
    }

    /**
     * Default result when model is unavailable or prediction fails.
     */
    private CreditClassifierResult defaultResult() {
        return CreditClassifierResult.builder()
                .creditScore(50) // Neutral score
                .defaultProbability(0.5)
                .noDefaultProbability(0.5)
                .confidence(0.5) // Max of probabilities
                .prediction("UNKNOWN")
                .featureImportance(Map.of())
                .modelVersion("none")
                .build();
    }

    /**
     * Extract feature importance from model (if supported).
     */
    private Map<String, Double> extractFeatureImportance(Model<Label> model) {
        // TODO: Extract feature importance from XGBoost model
        // Tribuo XGBoost models support getFeatureImportance() via model provenance
        // For now, return empty map
        return Map.of();
    }

    /**
     * Get model version from metadata.
     */
    private int getModelVersion(Model<Label> model) {
        // Extract from model metadata/provenance
        // For now, return 1
        return 1;
    }

    /**
     * Credit classifier prediction result.
     */
    @Builder
    public record CreditClassifierResult(
            int creditScore,              // 0-100 (100 = lowest default risk)
            double defaultProbability,    // 0.0-1.0
            double noDefaultProbability,  // 0.0-1.0
            double confidence,            // 0.0-1.0 (max of probabilities)
            String prediction,            // "DEFAULT" or "NO_DEFAULT"
            Map<String, Double> featureImportance,  // Feature name → importance score
            String modelVersion           // e.g., "credit_classifier-v1"
    ) {
    }
}
