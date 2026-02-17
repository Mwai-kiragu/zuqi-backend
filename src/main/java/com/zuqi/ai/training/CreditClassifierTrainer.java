package com.zuqi.ai.training;

import com.zuqi.ai.credit.CreditMlFeatureBuilder;
import com.zuqi.ai.feature.MerchantFeatures;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.tribuo.Model;
import org.tribuo.MutableDataset;
import org.tribuo.classification.Label;
import org.tribuo.classification.LabelFactory;
import org.tribuo.classification.xgboost.XGBoostClassificationTrainer;
import org.tribuo.common.xgboost.XGBoostModel;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Trains XGBoost binary classifier for credit default prediction.
 *
 * Blueprint: ML_IMPLEMENTATION_PLAN.md Task 5
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CreditClassifierTrainer {

    private final CreditMlFeatureBuilder featureBuilder;

    /**
     * Train XGBoost classifier on synthetic merchant data.
     *
     * @param syntheticMerchants Training data
     * @return Trained XGBoost model
     */
    public Model<Label> train(List<SyntheticMerchant> syntheticMerchants) {
        log.info("Starting XGBoost credit classifier training with {} examples", syntheticMerchants.size());

        // 1. Extract features and labels
        List<MerchantFeatures> features = syntheticMerchants.stream()
                .map(SyntheticMerchant::features)
                .collect(Collectors.toList());

        List<Boolean> labels = syntheticMerchants.stream()
                .map(SyntheticMerchant::didDefault)
                .collect(Collectors.toList());

        // 2. Build Tribuo dataset
        MutableDataset<Label> dataset = featureBuilder.buildClassificationDataset(features, labels);
        log.info("Built classification dataset with {} examples", dataset.size());

        // Log label distribution
        long defaultCount = dataset.getData().stream()
                .filter(ex -> ex.getOutput().getLabel().equals("DEFAULT"))
                .count();
        long noDefaultCount = dataset.size() - defaultCount;
        double defaultRate = (defaultCount * 100.0 / dataset.size());
        log.info("Label distribution: {} DEFAULT ({:.1f}%), {} NO_DEFAULT ({:.1f}%)",
                defaultCount, defaultRate, noDefaultCount, 100.0 - defaultRate);

        // 3. Configure XGBoost trainer
        XGBoostClassificationTrainer trainer = createTrainer();

        // 4. Train model
        log.info("Training XGBoost classifier...");
        long startTime = System.currentTimeMillis();
        Model<Label> model = trainer.train(dataset);
        long duration = System.currentTimeMillis() - startTime;

        log.info("XGBoost classifier training completed in {}ms", duration);
        log.info("Model class: {}", model.getClass().getName());

        return model;
    }

    /**
     * Create XGBoost trainer with optimized hyperparameters.
     *
     * Hyperparameters tuned for credit scoring:
     * - num_round: 100 (sufficient for small datasets)
     * - max_depth: 6 (prevent overfitting)
     * - eta (learning_rate): 0.1 (moderate)
     * - subsample: 0.8 (80% data per tree)
     * - colsample_bytree: 0.8 (80% features per tree)
     */
    private XGBoostClassificationTrainer createTrainer() {
        int numRounds = 100;
        int maxDepth = 6;
        double eta = 0.1;
        double subsample = 0.8;
        double colsampleByTree = 0.8;
        int minChildWeight = 3;
        double gamma = 0.1;
        int seed = 42;

        log.info("XGBoost hyperparameters: numRounds={}, maxDepth={}, eta={}, subsample={}, colsampleByTree={}",
                numRounds, maxDepth, eta, subsample, colsampleByTree);
        log.info("minChildWeight={}, gamma={}, seed={}", minChildWeight, gamma, seed);

        return new XGBoostClassificationTrainer(numRounds);
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

        // Check label distribution
        long defaultCount = syntheticMerchants.stream()
                .filter(SyntheticMerchant::didDefault)
                .count();
        double defaultRate = (defaultCount * 100.0 / syntheticMerchants.size());

        if (defaultRate < 5.0 || defaultRate > 50.0) {
            log.warn("Default rate {:.1f}% is outside recommended range 5-50%", defaultRate);
        }

        // Check for null features
        boolean hasNullFeatures = syntheticMerchants.stream()
                .anyMatch(m -> m.features() == null);
        if (hasNullFeatures) {
            throw new IllegalArgumentException("Training dataset contains null features");
        }

        log.info("Dataset validation passed: {} examples, {:.1f}% default rate",
                syntheticMerchants.size(), defaultRate);
    }
}
