package com.zuqi.ai.demand;

import com.zuqi.ai.feature.ExpiryFeatures;
import com.zuqi.ai.model.ModelRegistry;
import com.zuqi.ai.pipeline.ModelEvaluator;
import com.zuqi.ai.pipeline.XGBoostHyperparameterTuner;
import com.zuqi.ai.synthetic.generators.SyntheticExpiryBatchGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tribuo.Dataset;
import org.tribuo.Model;
import org.tribuo.Trainer;
import org.tribuo.classification.Label;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Training pipeline for expiry risk prediction model (Model #10).
 *
 * Pipeline:
 * 1. Generate synthetic batches (500)
 * 2. Build feature dataset with binary labels (HIGH_RISK / LOW_RISK)
 * 3. Split 80/20
 * 4. Tune and train XGBoost classifier
 * 5. Evaluate AUC-ROC (gate: AUC >= 0.70)
 * 6. Promote to ACTIVE
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExpiryRiskTrainingPipeline {

    private final SyntheticExpiryBatchGenerator batchGenerator;
    private final ExpiryRiskFeatureBuilder featureBuilder;
    private final ModelEvaluator modelEvaluator;
    private final ModelRegistry modelRegistry;
    private final Trainer<Label> xgBoostClassificationTrainer;
    private final XGBoostHyperparameterTuner hyperparameterTuner;

    public static final String MODEL_NAME = "expiry_risk_predictor";
    private static final double AUC_GATE = 0.70;

    @Transactional
    public TrainingResult runPipeline() {
        log.info("=== Starting Expiry Risk Training Pipeline ===");
        long start = System.currentTimeMillis();

        try {
            // Step 1: Generate synthetic batches
            List<SyntheticExpiryBatchGenerator.SyntheticExpiryBatch> batches =
                    batchGenerator.generateBatches(500);

            // Step 2: Build labelled examples (HIGH_RISK / LOW_RISK)
            List<ExpiryRiskFeatureBuilder.LabelledExpiryExample> examples = new ArrayList<>();
            for (var batch : batches) {
                ExpiryFeatures features = new ExpiryFeatures(
                        null, null, null,
                        batch.batchNumber(), batch.expiryDate(), batch.daysToExpiry(),
                        batch.currentStockQty(), batch.avgDailySalesRate(),
                        batch.projectedDaysToSell(), batch.similarSkuVelocity(),
                        batch.warehouseTurnoverRate(), batch.priceSensitivityScore(),
                        batch.batchAgeRatio()
                );
                examples.add(new ExpiryRiskFeatureBuilder.LabelledExpiryExample(
                        features, batch.sellThroughProbability()));
            }

            // Step 3: Shuffle then split 80/20 (prevents single-class test set)
            Collections.shuffle(examples, new Random(42L));
            int trainSize = (int) (examples.size() * 0.8);
            List<ExpiryRiskFeatureBuilder.LabelledExpiryExample> trainExamples =
                    new ArrayList<>(examples.subList(0, trainSize));
            List<ExpiryRiskFeatureBuilder.LabelledExpiryExample> testExamples =
                    new ArrayList<>(examples.subList(trainSize, examples.size()));

            // Step 4: Hyperparameter tuning + Train
            Dataset<Label> trainDataset = featureBuilder.buildClassificationDataset(trainExamples);
            XGBoostHyperparameterTuner.TunedModel<Label> tunedModel =
                    hyperparameterTuner.tuneAndTrainClassifier(trainDataset, ExpiryRiskFeatureBuilder.LABEL_HIGH_RISK);
            Model<Label> model = tunedModel.model();
            XGBoostHyperparameterTuner.TuningResult tuning = tunedModel.tuning();
            log.info("Training complete on {} examples (rounds={} eta={} maxDepth={})",
                    trainSize, tuning.bestNumRounds(), tuning.bestEta(), tuning.bestMaxDepth());

            // Step 5: Evaluate
            Dataset<Label> testDataset = featureBuilder.buildClassificationDataset(testExamples);
            ModelEvaluator.ClassifierEvaluationResult eval =
                    modelEvaluator.evaluateClassifier(model, testDataset, ExpiryRiskFeatureBuilder.LABEL_HIGH_RISK);

            boolean passed = eval.aucRoc() >= AUC_GATE;
            log.info("{} AUC={} (gate={})",
                    passed ? "PASSED" : "FAILED", String.format("%.4f", eval.aucRoc()), AUC_GATE);

            if (!passed) {
                return new TrainingResult(false, eval.aucRoc(), null, "Failed AUC gate");
            }

            // Step 6: Promote
            UUID modelId = promoteModel(model, eval, trainSize, tuning);

            long duration = System.currentTimeMillis() - start;
            log.info("=== Expiry Risk Pipeline complete in {}ms, modelId={} ===", duration, modelId);
            return new TrainingResult(true, eval.aucRoc(), modelId, null);

        } catch (Exception e) {
            log.error("Expiry risk training pipeline failed: {}", e.getMessage(), e);
            return new TrainingResult(false, -1.0, null, e.getMessage());
        }
    }

    private UUID promoteModel(Model<Label> model,
                               ModelEvaluator.ClassifierEvaluationResult eval,
                               int trainingSize,
                               XGBoostHyperparameterTuner.TuningResult tuning) throws Exception {

        byte[] modelBytes;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(model);
            modelBytes = baos.toByteArray();
        }

        Map<String, Object> hyperparameters = new HashMap<>();
        hyperparameters.put("algorithm", "xgboost_classification");
        hyperparameters.put("positive_label", ExpiryRiskFeatureBuilder.LABEL_HIGH_RISK);
        hyperparameters.put("tuned_num_rounds", tuning.bestNumRounds());
        hyperparameters.put("tuned_eta", tuning.bestEta());
        hyperparameters.put("tuned_max_depth", tuning.bestMaxDepth());
        hyperparameters.put("tuning_cv_auc", tuning.bestScore());

        com.zuqi.domain.ai.AIModelRegistry registry = modelRegistry.registerModel(
                MODEL_NAME, "xgboost_classification", hyperparameters, "training_pipeline");

        Map<String, Object> metrics = new HashMap<>();
        metrics.put("auc_roc", eval.aucRoc());
        metrics.put("accuracy", eval.accuracy());
        metrics.put("precision", eval.precision());
        metrics.put("recall", eval.recall());
        metrics.put("f1", eval.f1Score());
        metrics.put("training_size", trainingSize);

        modelRegistry.updateModelAfterTraining(registry.getId(), metrics, modelBytes,
                Map.of("feature_count", featureBuilder.getFeatureCount()));

        modelRegistry.promoteToActive(registry.getId());

        return registry.getId();
    }

    public record TrainingResult(
            boolean success,
            double aucRoc,
            UUID modelId,
            String errorMessage
    ) {}
}
