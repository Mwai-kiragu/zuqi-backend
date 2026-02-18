package com.zuqi.ai.prediction;

import com.zuqi.ai.feature.InventoryFeatures;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.tribuo.Dataset;
import org.tribuo.Model;
import org.tribuo.classification.Label;
import org.tribuo.classification.xgboost.XGBoostClassificationTrainer;

import java.util.List;

/**
 * Trains an XGBoost classifier for stockout prediction.
 *
 * Blueprint reference: implementation_plan.md Phase 4, Step 7a
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StockoutModelTrainer {

    private static final int MINIMUM_DATASET_SIZE = 300;
    private static final int NUM_ROUNDS           = 100;

    private final StockoutFeatureBuilder featureBuilder;

    /**
     * Train XGBoost classifier on labelled inventory snapshots.
     *
     * @param featuresList List of inventory feature snapshots
     * @param labels       Corresponding labels ("STOCKOUT" or "NO_STOCKOUT")
     * @return Trained Model<Label>
     */
    public Model<Label> train(List<InventoryFeatures> featuresList, List<String> labels) {
        log.info("Training stockout predictor: {} examples", featuresList.size());

        if (featuresList.size() < MINIMUM_DATASET_SIZE) {
            log.warn("Training set {} below minimum {}", featuresList.size(), MINIMUM_DATASET_SIZE);
        }

        Dataset<Label> dataset = featureBuilder.buildDataset(featuresList, labels);

        XGBoostClassificationTrainer trainer = new XGBoostClassificationTrainer(NUM_ROUNDS);
        Model<Label> model = trainer.train(dataset);

        log.info("Stockout predictor training complete. Dataset size: {}", dataset.size());
        return model;
    }

    public int getMinimumDatasetSize() {
        return MINIMUM_DATASET_SIZE;
    }
}
