package com.zuqi.ai.anomaly;

import com.zuqi.ai.event.OrderCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.tribuo.Model;
import org.tribuo.MutableDataset;
import org.tribuo.Trainer;
import org.tribuo.classification.Label;

import java.util.List;

/**
 * Trains the Tier-2 data quality XGBoost classifier.
 *
 * Uses a supervised binary classification approach (NORMAL / ANOMALOUS)
 * on 14 features from {@link DataQualityFeatureBuilder}.
 *
 * During synthetic phase the training set is constructed by
 * {@link DataQualityTrainingPipeline}: ~97% NORMAL orders from normal
 * synthetic merchants + ~3% deliberately corrupted ANOMALOUS orders.
 *
 * Blueprint reference: plan.md Section 6.3 - DataQualityDetector Tier-2
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DataQualityModelTrainer {

    static final int  MINIMUM_DATASET_SIZE    = 200;
    static final int  MINIMUM_ANOMALOUS_COUNT = 20;

    private final DataQualityFeatureBuilder  featureBuilder;
    @Qualifier("xgBoostClassificationTrainer")
    private final Trainer<Label>             classificationTrainer;

    /**
     * Train the data quality classifier.
     *
     * @param normalOrders   Known-good order events
     * @param anomalousOrders Injected bad order events
     * @return Trained Tribuo Model<Label>
     */
    public Model<Label> train(List<OrderCreatedEvent> normalOrders,
                               List<OrderCreatedEvent> anomalousOrders) {
        int total = normalOrders.size() + anomalousOrders.size();
        log.info("Training data quality classifier: {} normal, {} anomalous, {} total",
                normalOrders.size(), anomalousOrders.size(), total);

        if (total < MINIMUM_DATASET_SIZE) {
            log.warn("Dataset size {} below minimum {}; classifier may have poor precision",
                    total, MINIMUM_DATASET_SIZE);
        }
        if (anomalousOrders.size() < MINIMUM_ANOMALOUS_COUNT) {
            log.warn("Only {} anomalous examples — minimum {} recommended for good recall",
                    anomalousOrders.size(), MINIMUM_ANOMALOUS_COUNT);
        }

        MutableDataset<Label> dataset = featureBuilder.buildTrainingDataset(normalOrders, anomalousOrders);
        Model<Label> model = classificationTrainer.train(dataset);

        log.info("Data quality classifier training complete. Dataset: {}", dataset.size());
        return model;
    }
}
