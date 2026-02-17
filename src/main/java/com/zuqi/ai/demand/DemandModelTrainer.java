package com.zuqi.ai.demand;

import com.zuqi.ai.feature.DemandFeatures;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.tribuo.Model;
import org.tribuo.MutableDataset;
import org.tribuo.regression.Regressor;
import org.tribuo.regression.xgboost.XGBoostRegressionTrainer;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Trains XGBoost regressor for demand forecasting.
 *
 * Blueprint: plan.md Section 6.2 - Demand Forecasting Module
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DemandModelTrainer {

    private final DemandFeatureBuilder featureBuilder;

    /**
     * Train XGBoost demand forecasting model on historical order data.
     *
     * @param trainingData List of (features, actual quantity) pairs
     * @return Trained XGBoost model
     */
    public Model<Regressor> train(List<DemandTrainingExample> trainingData) {
        log.info("Starting XGBoost demand forecasting training with {} examples", trainingData.size());

        // 1. Extract features and targets
        List<DemandFeatures> features = trainingData.stream()
                .map(DemandTrainingExample::features)
                .collect(Collectors.toList());

        List<BigDecimal> quantities = trainingData.stream()
                .map(DemandTrainingExample::actualQuantity)
                .collect(Collectors.toList());

        // 2. Build Tribuo dataset
        MutableDataset<Regressor> dataset = featureBuilder.buildRegressionDataset(features, quantities);
        log.info("Built demand forecasting dataset with {} examples", dataset.size());

        // Log target statistics
        BigDecimal minQty = quantities.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal maxQty = quantities.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        double avgQty = quantities.stream()
                .mapToDouble(BigDecimal::doubleValue)
                .average()
                .orElse(0.0);

        log.info("Target quantities - Min: {}, Max: {}, Avg: {:.2f}",
                minQty, maxQty, avgQty);

        // 3. Configure XGBoost trainer
        XGBoostRegressionTrainer trainer = createTrainer();

        // 4. Train model
        log.info("Training XGBoost demand forecaster...");
        long startTime = System.currentTimeMillis();
        Model<Regressor> model = trainer.train(dataset);
        long duration = System.currentTimeMillis() - startTime;

        log.info("XGBoost demand forecaster training completed in {}ms", duration);
        log.info("Model class: {}", model.getClass().getName());

        return model;
    }

    /**
     * Create XGBoost trainer with optimized hyperparameters for demand forecasting.
     *
     * Hyperparameters tuned for time-series regression:
     * - num_round: 150 (more rounds for temporal patterns)
     * - max_depth: 5 (prevent overfitting on sparse data)
     * - eta (learning_rate): 0.05 (slower learning for stability)
     * - subsample: 0.8 (80% data per tree)
     * - colsample_bytree: 0.8 (80% features per tree)
     */
    private XGBoostRegressionTrainer createTrainer() {
        int numRounds = 150;
        int maxDepth = 5;
        double eta = 0.05;
        double subsample = 0.8;
        double colsampleByTree = 0.8;
        int minChildWeight = 5;
        double gamma = 0.0; // No regularization by default
        int seed = 42;

        log.info("XGBoost demand forecasting hyperparameters: numRounds={}, maxDepth={}, eta={}, subsample={}, colsampleByTree={}",
                numRounds, maxDepth, eta, subsample, colsampleByTree);
        log.info("minChildWeight={}, gamma={}, seed={}", minChildWeight, gamma, seed);

        return new XGBoostRegressionTrainer(numRounds);
    }

    /**
     * Get recommended minimum training dataset size.
     *
     * @return Minimum number of examples (1000)
     */
    public int getMinimumDatasetSize() {
        return 1000; // Need more data for time-series patterns
    }

    /**
     * Validate dataset quality before training.
     *
     * @param trainingData Training data
     * @throws IllegalArgumentException if dataset is invalid
     */
    public void validateDataset(List<DemandTrainingExample> trainingData) {
        if (trainingData == null || trainingData.isEmpty()) {
            throw new IllegalArgumentException("Training dataset cannot be empty");
        }

        if (trainingData.size() < getMinimumDatasetSize()) {
            throw new IllegalArgumentException(String.format(
                    "Training dataset too small: %d examples (minimum: %d)",
                    trainingData.size(), getMinimumDatasetSize()
            ));
        }

        // Check for null features
        boolean hasNullFeatures = trainingData.stream()
                .anyMatch(ex -> ex.features() == null || ex.actualQuantity() == null);
        if (hasNullFeatures) {
            throw new IllegalArgumentException("Training dataset contains null features or quantities");
        }

        // Check for negative quantities
        boolean hasNegativeQty = trainingData.stream()
                .anyMatch(ex -> ex.actualQuantity().compareTo(BigDecimal.ZERO) < 0);
        if (hasNegativeQty) {
            throw new IllegalArgumentException("Training dataset contains negative quantities");
        }

        log.info("Dataset validation passed: {} examples", trainingData.size());
    }

    /**
     * Training example for demand forecasting.
     */
    public record DemandTrainingExample(
            DemandFeatures features,
            BigDecimal actualQuantity
    ) {
    }
}
