package com.zuqi.ai.pricing;

import com.zuqi.ai.model.ModelRegistry;
import com.zuqi.ai.pipeline.ModelEvaluator;
import com.zuqi.ai.synthetic.SyntheticDataBundle;
import com.zuqi.ai.synthetic.SyntheticDataConfig;
import com.zuqi.ai.synthetic.SyntheticDataOrchestrator;
import com.zuqi.ai.synthetic.SyntheticPricingFeatureBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tribuo.Dataset;
import org.tribuo.Model;
import org.tribuo.MutableDataset;
import org.tribuo.Trainer;
import org.tribuo.regression.Regressor;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Training pipeline for smart pricing XGBoost regressor (Model #17).
 *
 * Pipeline:
 * 1. Generate synthetic order-item data (500 merchants × their orders)
 * 2. Build pricing feature dataset (price → quantity mapping)
 * 3. Split 80/20
 * 4. Train XGBoost regressor
 * 5. Evaluate RMSE (gate: RMSE < 50 units)
 * 6. Promote to ACTIVE
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PricingTrainingPipeline {

    private final SyntheticDataOrchestrator dataOrchestrator;
    private final SyntheticPricingFeatureBuilder syntheticFeatureBuilder;
    private final ModelEvaluator modelEvaluator;
    private final ModelRegistry modelRegistry;
    private final Trainer<Regressor> xgBoostRegressionTrainer;

    public static final String MODEL_NAME = "smart_pricing_recommender";
    private static final double RMSE_GATE = 50.0; // units/week

    @Transactional
    public TrainingResult runPipeline() {
        log.info("=== Starting Smart Pricing Training Pipeline ===");
        long start = System.currentTimeMillis();

        try {
            // Step 1: Generate synthetic data
            UUID placeholderDistributorId = UUID.randomUUID();
            SyntheticDataConfig config = SyntheticDataConfig.defaultConfig(
                    placeholderDistributorId, 42L);
            SyntheticDataBundle bundle = dataOrchestrator.generateBundle(config);

            // Step 2: Build dataset
            MutableDataset<Regressor> fullDataset =
                    syntheticFeatureBuilder.buildDataset(bundle, placeholderDistributorId);

            if (fullDataset.size() < 10) {
                return new TrainingResult(false, -1.0, null,
                        "Insufficient training examples: " + fullDataset.size());
            }

            // Step 3: Split 80/20
            int trainSize = (int) (fullDataset.size() * 0.8);
            Dataset<Regressor> trainDataset = splitDataset(fullDataset, 0, trainSize);
            Dataset<Regressor> testDataset  = splitDataset(fullDataset, trainSize, fullDataset.size());

            // Step 4: Train
            Model<Regressor> model = xgBoostRegressionTrainer.train(trainDataset);
            log.info("Training complete on {} examples", trainSize);

            // Step 5: Evaluate
            ModelEvaluator.RegressorEvaluationResult eval =
                    modelEvaluator.evaluateRegressor(model, testDataset);

            boolean passed = eval.rmse() < RMSE_GATE;
            log.info("{} RMSE={} (gate={})",
                    passed ? "PASSED" : "FAILED", String.format("%.2f", eval.rmse()), RMSE_GATE);

            if (!passed) {
                return new TrainingResult(false, eval.rmse(), null, "Failed RMSE gate");
            }

            // Step 6: Promote
            UUID modelId = promoteModel(model, eval, trainSize);

            long duration = System.currentTimeMillis() - start;
            log.info("=== Smart Pricing Pipeline complete in {}ms, modelId={} ===", duration, modelId);
            return new TrainingResult(true, eval.rmse(), modelId, null);

        } catch (Exception e) {
            log.error("Smart pricing training pipeline failed: {}", e.getMessage(), e);
            return new TrainingResult(false, -1.0, null, e.getMessage());
        }
    }

    private Dataset<Regressor> splitDataset(MutableDataset<Regressor> full, int from, int to) {
        org.tribuo.provenance.SimpleDataSourceProvenance prov =
                new org.tribuo.provenance.SimpleDataSourceProvenance(
                        "PricingSplit", full.getOutputFactory());
        org.tribuo.MutableDataset<Regressor> split =
                new org.tribuo.MutableDataset<>(prov, full.getOutputFactory());
        for (int i = from; i < to; i++) {
            split.add(full.getExample(i));
        }
        return split;
    }

    private UUID promoteModel(Model<Regressor> model,
                               ModelEvaluator.RegressorEvaluationResult eval,
                               int trainingSize) throws Exception {
        byte[] modelBytes;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(model);
            modelBytes = baos.toByteArray();
        }

        Map<String, Object> hyperparameters = new HashMap<>();
        hyperparameters.put("algorithm", "xgboost_regression");
        hyperparameters.put("num_rounds", 50);
        hyperparameters.put("rmse_gate", RMSE_GATE);

        com.zuqi.domain.ai.AIModelRegistry registry = modelRegistry.registerModel(
                MODEL_NAME, "xgboost_regression", hyperparameters, "training_pipeline");

        Map<String, Object> metrics = new HashMap<>();
        metrics.put("rmse", eval.rmse());
        metrics.put("r2", eval.r2());
        metrics.put("mae", eval.mae());
        metrics.put("training_size", trainingSize);

        modelRegistry.updateModelAfterTraining(registry.getId(), metrics, modelBytes,
                Map.of("feature_count", 12));

        modelRegistry.promoteToActive(registry.getId());

        return registry.getId();
    }

    public record TrainingResult(
            boolean success,
            double rmse,
            UUID modelId,
            String errorMessage
    ) {}
}
