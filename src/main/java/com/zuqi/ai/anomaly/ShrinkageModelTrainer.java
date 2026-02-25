package com.zuqi.ai.anomaly;

import com.zuqi.ai.feature.InventoryFeatures;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.tribuo.Model;
import org.tribuo.MutableDataset;
import org.tribuo.anomaly.Event;
import org.tribuo.anomaly.libsvm.LibSVMAnomalyTrainer;
import org.tribuo.anomaly.libsvm.SVMAnomalyType;
import org.tribuo.common.libsvm.KernelType;
import org.tribuo.common.libsvm.SVMParameters;

import java.util.List;

/**
 * Trains a LibSVM one-class anomaly detection model on normal inventory data.
 *
 * Blueprint reference: plan.md Section 6.3 - ShrinkageModelTrainer
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ShrinkageModelTrainer {

    private static final int MINIMUM_DATASET_SIZE = 200;

    private final AnomalyFeatureBuilder anomalyFeatureBuilder;

    /**
     * Train a one-class SVM on normal inventory snapshots.
     *
     * @param normalData List of normal (non-anomalous) inventory feature snapshots
     * @return Trained Tribuo Model<Event>
     */
    public Model<Event> train(List<InventoryFeatures> normalData) {
        log.info("Training shrinkage detector on {} normal examples", normalData.size());

        if (normalData.size() < MINIMUM_DATASET_SIZE) {
            log.warn("Training set size {} is below minimum {}; model may have high false-positive rate",
                    normalData.size(), MINIMUM_DATASET_SIZE);
        }

        // Build training dataset (all EXPECTED labels)
        MutableDataset<Event> dataset = anomalyFeatureBuilder.buildInventoryDataset(normalData);

        // One-class SVM with RBF kernel
        SVMParameters<Event> params = new SVMParameters<>(
                new SVMAnomalyType(SVMAnomalyType.SVMMode.ONE_CLASS),
                KernelType.RBF
        );
        LibSVMAnomalyTrainer trainer = new LibSVMAnomalyTrainer(params);

        Model<Event> model = trainer.train(dataset);
        log.info("Shrinkage detector training complete. Dataset size: {}", dataset.size());
        return model;
    }

    public int getMinimumDatasetSize() {
        return MINIMUM_DATASET_SIZE;
    }
}
