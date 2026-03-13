package com.zuqi.ai.credit;

import com.zuqi.ai.feature.MerchantFeatures;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.tribuo.Model;
import org.tribuo.MutableDataset;
import org.tribuo.regression.Regressor;
import org.tribuo.regression.xgboost.XGBoostRegressionTrainer;

import java.math.BigDecimal;
import java.util.List;

/**
 * Trains XGBoost regressor for credit limit prediction.
 *
 * <p>Accepts pre-extracted feature vectors and pre-computed target limits,
 * removing any dependency on the old {@code SyntheticMerchant} ML wrapper.
 * Callers are responsible for computing the target credit limits
 * (e.g., via {@link CreditLimitRegressor#calculateIdealLimit}) before training.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CreditLimitRegressorTrainer {

    private final CreditMlFeatureBuilder featureBuilder;

    /**
     * Train XGBoost regressor on merchant feature data.
     *
     * @param features     Feature vectors (one per merchant)
     * @param targetLimits Pre-computed ideal credit limits (training targets)
     * @return Trained XGBoost model
     */
    public Model<Regressor> train(List<MerchantFeatures> features, List<BigDecimal> targetLimits) {
        log.info("Starting XGBoost credit limit regressor training with {} examples", features.size());

        // Log target statistics
        BigDecimal minLimit = targetLimits.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal maxLimit = targetLimits.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        double avgLimit = targetLimits.stream()
                .mapToDouble(BigDecimal::doubleValue)
                .average()
                .orElse(0.0);
        log.info("Target credit limits - Min: {}, Max: {}, Avg: {:.0f}", minLimit, maxLimit, avgLimit);

        // Build Tribuo dataset
        MutableDataset<Regressor> dataset = featureBuilder.buildRegressionDataset(features, targetLimits);
        log.info("Built regression dataset with {} examples", dataset.size());

        // Configure XGBoost trainer
        XGBoostRegressionTrainer trainer = new XGBoostRegressionTrainer(100);

        // Train model
        log.info("Training XGBoost regressor...");
        long startTime = System.currentTimeMillis();
        Model<Regressor> model = trainer.train(dataset);
        long duration = System.currentTimeMillis() - startTime;

        log.info("XGBoost regressor training completed in {}ms", duration);
        return model;
    }

    /**
     * Get recommended minimum training dataset size.
     */
    public int getMinimumDatasetSize() {
        return 500;
    }

    /**
     * Validate dataset quality before training.
     *
     * @param datasetSize Number of training examples
     * @throws IllegalArgumentException if dataset is too small
     */
    public void validateDataset(int datasetSize) {
        if (datasetSize < getMinimumDatasetSize()) {
            throw new IllegalArgumentException(String.format(
                    "Training dataset too small: %d examples (minimum: %d)",
                    datasetSize, getMinimumDatasetSize()));
        }
        log.info("Dataset validation passed: {} examples", datasetSize);
    }
}
