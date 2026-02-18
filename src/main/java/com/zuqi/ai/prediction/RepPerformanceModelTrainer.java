package com.zuqi.ai.prediction;

import com.zuqi.ai.feature.SalesRepFeatures;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.tribuo.Dataset;
import org.tribuo.Model;
import org.tribuo.regression.Regressor;
import org.tribuo.regression.xgboost.XGBoostRegressionTrainer;

import java.util.List;

/**
 * Trains an XGBoost regressor for sales rep performance prediction.
 *
 * Blueprint reference: implementation_plan.md Phase 4, Step 7b
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RepPerformanceModelTrainer {

    private static final int MINIMUM_DATASET_SIZE = 200;
    private static final int NUM_ROUNDS           = 150;

    private final RepPerformanceFeatureBuilder featureBuilder;

    /**
     * Train XGBoost regressor on labelled rep performance snapshots.
     *
     * @param featuresList List of rep feature snapshots
     * @param scores       Corresponding performance scores (0–100)
     * @return Trained Model<Regressor>
     */
    public Model<Regressor> train(List<SalesRepFeatures> featuresList, List<Double> scores) {
        log.info("Training rep performance predictor: {} examples", featuresList.size());

        if (featuresList.size() < MINIMUM_DATASET_SIZE) {
            log.warn("Training set {} below minimum {}", featuresList.size(), MINIMUM_DATASET_SIZE);
        }

        Dataset<Regressor> dataset = featureBuilder.buildDataset(featuresList, scores);

        XGBoostRegressionTrainer trainer = new XGBoostRegressionTrainer(NUM_ROUNDS);
        Model<Regressor> model = trainer.train(dataset);

        log.info("Rep performance predictor training complete. Dataset size: {}", dataset.size());
        return model;
    }

    public int getMinimumDatasetSize() {
        return MINIMUM_DATASET_SIZE;
    }
}
