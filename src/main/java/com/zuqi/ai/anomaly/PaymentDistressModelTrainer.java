package com.zuqi.ai.anomaly;

import com.zuqi.ai.feature.MerchantPaymentTrendFeatures;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.tribuo.Dataset;
import org.tribuo.Model;
import org.tribuo.classification.Label;
import org.tribuo.classification.xgboost.XGBoostClassificationTrainer;

import java.util.List;

/**
 * Trains an XGBoost classifier for payment distress prediction.
 *
 * <p>Binary classification: DISTRESS vs NO_DISTRESS, trained on
 * {@link MerchantPaymentTrendFeatures} (20 features covering 3-month
 * payment-behaviour trends).
 *
 * <p>Blueprint reference: implementation_plan.md Phase 6 — Payment Distress Classification
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentDistressModelTrainer {

    private static final int MINIMUM_DATASET_SIZE = 200;
    private static final int NUM_ROUNDS           = 100;

    private final PaymentDistressFeatureBuilder featureBuilder;

    /**
     * Train XGBoost classifier on labelled merchant trend features.
     *
     * @param featuresList List of merchant payment trend features
     * @param labels       Corresponding labels ("DISTRESS" or "NO_DISTRESS")
     * @return Trained Model&lt;Label&gt;
     */
    public Model<Label> train(List<MerchantPaymentTrendFeatures> featuresList, List<String> labels) {
        log.info("Training payment distress classifier: {} examples", featuresList.size());

        if (featuresList.size() < MINIMUM_DATASET_SIZE) {
            log.warn("Training set {} below minimum {}", featuresList.size(), MINIMUM_DATASET_SIZE);
        }

        Dataset<Label> dataset = featureBuilder.buildDataset(featuresList, labels);

        XGBoostClassificationTrainer trainer = new XGBoostClassificationTrainer(NUM_ROUNDS);
        Model<Label> model = trainer.train(dataset);

        log.info("Payment distress classifier training complete. Dataset size: {}", dataset.size());
        return model;
    }

    public int getMinimumDatasetSize() {
        return MINIMUM_DATASET_SIZE;
    }
}
