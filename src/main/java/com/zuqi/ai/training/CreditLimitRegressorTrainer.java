package com.zuqi.ai.training;

import com.zuqi.ai.credit.CreditLimitRegressor;
import com.zuqi.ai.credit.CreditMlFeatureBuilder;
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
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Trains XGBoost regressor for credit limit prediction.
 *
 * Blueprint: ML_IMPLEMENTATION_PLAN.md Task 5
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CreditLimitRegressorTrainer {

    private final CreditMlFeatureBuilder featureBuilder;
    private final CreditLimitRegressor creditLimitRegressor;

    /**
     * Train XGBoost regressor on synthetic merchant data.
     *
     * @param syntheticMerchants Training data
     * @return Trained XGBoost model
     */
    public Model<Regressor> train(List<SyntheticMerchant> syntheticMerchants) {
        log.info("Starting XGBoost credit limit regressor training with {} examples", syntheticMerchants.size());

        // 1. Extract features
        List<MerchantFeatures> features = syntheticMerchants.stream()
                .map(SyntheticMerchant::features)
                .collect(Collectors.toList());

        // 2. Calculate ideal credit limits (training targets)
        List<BigDecimal> targetLimits = syntheticMerchants.stream()
                .map(m -> creditLimitRegressor.calculateIdealLimit(
                        m.features(),
                        m.defaultProbability()
                ))
                .collect(Collectors.toList());

        // Log target statistics
        BigDecimal minLimit = targetLimits.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal maxLimit = targetLimits.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        double avgLimit = targetLimits.stream()
                .mapToDouble(BigDecimal::doubleValue)
                .average()
                .orElse(0.0);

        log.info("Target credit limits - Min: {}, Max: {}, Avg: {:.0f}",
                minLimit, maxLimit, avgLimit);

        // 3. Build Tribuo dataset
        MutableDataset<Regressor> dataset = featureBuilder.buildRegressionDataset(features, targetLimits);
        log.info("Built regression dataset with {} examples", dataset.size());

        // 4. Configure XGBoost trainer
        XGBoostRegressionTrainer trainer = createTrainer();

        // 5. Train model
        log.info("Training XGBoost regressor...");
        long startTime = System.currentTimeMillis();
        Model<Regressor> model = trainer.train(dataset);
        long duration = System.currentTimeMillis() - startTime;

        log.info("XGBoost regressor training completed in {}ms", duration);
        log.info("Model class: {}", model.getClass().getName());

        return model;
    }

    /**
     * Create XGBoost regressor trainer with optimized hyperparameters.
     *
     * Hyperparameters tuned for credit limit regression:
     * - num_round: 100 (sufficient for small datasets)
     * - max_depth: 6 (prevent overfitting)
     * - eta (learning_rate): 0.1 (moderate)
     * - subsample: 0.8 (80% data per tree)
     * - colsample_bytree: 0.8 (80% features per tree)
     */
    private XGBoostRegressionTrainer createTrainer() {
        int numRounds = 100;
        int maxDepth = 6;
        double eta = 0.1;
        double subsample = 0.8;
        double colsampleByTree = 0.8;
        int minChildWeight = 3;
        double gamma = 0.1;
        int seed = 42;

        log.info("XGBoost regressor hyperparameters: numRounds={}, maxDepth={}, eta={}, subsample={}, colsampleByTree={}",
                numRounds, maxDepth, eta, subsample, colsampleByTree);
        log.info("minChildWeight={}, gamma={}, seed={}", minChildWeight, gamma, seed);

        return new XGBoostRegressionTrainer(numRounds);
    }

    /**
     * Get recommended minimum training dataset size.
     *
     * @return Minimum number of examples (500)
     */
    public int getMinimumDatasetSize() {
        return 500; // Lower minimum for initial training
    }

    /**
     * Validate dataset quality before training.
     *
     * @param syntheticMerchants Training data
     * @throws IllegalArgumentException if dataset is invalid
     */
    public void validateDataset(List<SyntheticMerchant> syntheticMerchants) {
        if (syntheticMerchants == null || syntheticMerchants.isEmpty()) {
            throw new IllegalArgumentException("Training dataset cannot be empty");
        }

        if (syntheticMerchants.size() < getMinimumDatasetSize()) {
            throw new IllegalArgumentException(String.format(
                    "Training dataset too small: %d examples (minimum: %d)",
                    syntheticMerchants.size(), getMinimumDatasetSize()
            ));
        }

        // Check for null features
        boolean hasNullFeatures = syntheticMerchants.stream()
                .anyMatch(m -> m.features() == null);
        if (hasNullFeatures) {
            throw new IllegalArgumentException("Training dataset contains null features");
        }

        log.info("Dataset validation passed: {} examples", syntheticMerchants.size());
    }
}
