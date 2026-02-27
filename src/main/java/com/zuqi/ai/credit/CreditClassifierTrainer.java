package com.zuqi.ai.credit;

import com.zuqi.ai.feature.MerchantFeatures;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.tribuo.Model;
import org.tribuo.MutableDataset;
import org.tribuo.classification.Label;
import org.tribuo.classification.xgboost.XGBoostClassificationTrainer;

import java.util.List;

/**
 * Trains XGBoost binary classifier for credit default prediction.
 *
 * <p>Accepts pre-extracted feature and label lists, removing any
 * dependency on the old {@code SyntheticMerchant} ML wrapper.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CreditClassifierTrainer {

    private final CreditMlFeatureBuilder featureBuilder;

    /**
     * Train XGBoost classifier on merchant feature data.
     *
     * @param features Feature vectors (one per merchant)
     * @param labels   Default labels (true = DEFAULT, false = NO_DEFAULT)
     * @return Trained XGBoost model
     */
    public Model<Label> train(List<MerchantFeatures> features, List<Boolean> labels) {
        log.info("Starting XGBoost credit classifier training with {} examples", features.size());

        // Build Tribuo dataset
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

        // Configure XGBoost trainer
        XGBoostClassificationTrainer trainer = new XGBoostClassificationTrainer(100);

        // Train model
        log.info("Training XGBoost classifier...");
        long startTime = System.currentTimeMillis();
        Model<Label> model = trainer.train(dataset);
        long duration = System.currentTimeMillis() - startTime;

        log.info("XGBoost classifier training completed in {}ms", duration);
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
     * @param datasetSize  Number of training examples
     * @param defaultRate  Fraction of examples with DEFAULT label (0.0–1.0)
     * @throws IllegalArgumentException if dataset is too small
     */
    public void validateDataset(int datasetSize, double defaultRate) {
        if (datasetSize < getMinimumDatasetSize()) {
            throw new IllegalArgumentException(String.format(
                    "Training dataset too small: %d examples (minimum: %d)",
                    datasetSize, getMinimumDatasetSize()));
        }

        if (defaultRate < 0.05 || defaultRate > 0.50) {
            log.warn("Default rate {:.1f}% is outside recommended range 5–50%", defaultRate * 100);
        }

        log.info("Dataset validation passed: {} examples, {:.1f}% default rate",
                datasetSize, defaultRate * 100);
    }
}
