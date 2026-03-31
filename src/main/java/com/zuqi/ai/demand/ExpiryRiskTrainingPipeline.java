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
import org.tribuo.regression.Regressor;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Training pipeline for expiry risk prediction model (Model #10).
 *
 * <p>Uses XGBoost <strong>regression</strong> to predict sell-through probability
 * (0.0 = expires unsold → high risk, 1.0 = sells out → low risk).
 * The continuous output is then inverted by {@link ExpiryRiskPredictor} to derive
 * {@code riskScore = 1 - sellThroughProbability}.
 *
 * <p>Pipeline:
 * <ol>
 *   <li>Generate 500 synthetic batches</li>
 *   <li>Build regression dataset (target: sell_through_probability 0.0–1.0)</li>
 *   <li>Shuffle + split 80/20</li>
 *   <li>Hyperparameter-tune and train XGBoost regressor</li>
 *   <li>Evaluate R² (quality gate: R² ≥ 0.65)</li>
 *   <li>Promote to ACTIVE</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExpiryRiskTrainingPipeline {

    private final SyntheticExpiryBatchGenerator batchGenerator;
    private final ExpiryRiskFeatureBuilder       featureBuilder;
    private final ModelEvaluator                 modelEvaluator;
    private final ModelRegistry                  modelRegistry;
    private final Trainer<Regressor>             xgBoostRegressionTrainer;
    private final XGBoostHyperparameterTuner     hyperparameterTuner;

    public static final String MODEL_NAME = "expiry_risk_predictor";
    private static final double R2_GATE   = 0.65;

    @Transactional
    public TrainingResult runPipeline() {
        log.info("=== Starting Expiry Risk Training Pipeline (Regression) ===");
        long start = System.currentTimeMillis();

        try {
            // Step 1: Generate synthetic batches
            List<SyntheticExpiryBatchGenerator.SyntheticExpiryBatch> batches =
                    batchGenerator.generateBatches(500);

            // Step 2: Build regression examples (target = sell_through_probability 0.0-1.0)
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

            // Step 3: Shuffle then split 80/20
            Collections.shuffle(examples, new Random(42L));
            int trainSize = (int) (examples.size() * 0.8);
            List<ExpiryRiskFeatureBuilder.LabelledExpiryExample> trainExamples =
                    new ArrayList<>(examples.subList(0, trainSize));
            List<ExpiryRiskFeatureBuilder.LabelledExpiryExample> testExamples =
                    new ArrayList<>(examples.subList(trainSize, examples.size()));

            // Step 4: Tune and train (regression)
            Dataset<Regressor> trainDataset = featureBuilder.buildDataset(trainExamples);
            XGBoostHyperparameterTuner.TunedModel<Regressor> tunedModel =
                    hyperparameterTuner.tuneAndTrainRegressor(trainDataset);
            Model<Regressor> model = tunedModel.model();
            XGBoostHyperparameterTuner.TuningResult tuning = tunedModel.tuning();
            log.info("Training complete on {} examples (rounds={} eta={} maxDepth={})",
                    trainSize, tuning.bestNumRounds(), tuning.bestEta(), tuning.bestMaxDepth());

            // Step 5: Evaluate (regression metrics)
            Dataset<Regressor> testDataset = featureBuilder.buildDataset(testExamples);
            ModelEvaluator.RegressorEvaluationResult eval =
                    modelEvaluator.evaluateRegressor(model, testDataset);

            boolean passed = eval.r2() >= R2_GATE;
            log.info("{} R2={} MAE={} RMSE={} (gate=R2>={})",
                    passed ? "PASSED" : "FAILED",
                    String.format("%.4f", eval.r2()),
                    String.format("%.4f", eval.mae()),
                    String.format("%.4f", eval.rmse()),
                    R2_GATE);

            if (!passed) {
                return new TrainingResult(false, eval.r2(), eval.mae(), eval.rmse(), null,
                        "Failed R2 gate (" + String.format("%.4f", eval.r2()) + " < " + R2_GATE + ")");
            }

            // Step 6: Promote
            UUID modelId = promoteModel(model, eval, trainSize, tuning);

            long duration = System.currentTimeMillis() - start;
            log.info("=== Expiry Risk Pipeline complete in {}ms, modelId={} ===", duration, modelId);
            return new TrainingResult(true, eval.r2(), eval.mae(), eval.rmse(), modelId, null);

        } catch (Exception e) {
            log.error("Expiry risk training pipeline failed: {}", e.getMessage(), e);
            return new TrainingResult(false, -1.0, -1.0, -1.0, null, e.getMessage());
        }
    }

    private UUID promoteModel(Model<Regressor> model,
                               ModelEvaluator.RegressorEvaluationResult eval,
                               int trainingSize,
                               XGBoostHyperparameterTuner.TuningResult tuning) throws Exception {

        byte[] modelBytes;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos  = new ObjectOutputStream(baos)) {
            oos.writeObject(model);
            modelBytes = baos.toByteArray();
        }

        Map<String, Object> hyperparameters = new HashMap<>();
        hyperparameters.put("algorithm",         "xgboost_regression");
        hyperparameters.put("target",             "sell_through_probability");
        hyperparameters.put("tuned_num_rounds",   tuning.bestNumRounds());
        hyperparameters.put("tuned_eta",          tuning.bestEta());
        hyperparameters.put("tuned_max_depth",    tuning.bestMaxDepth());
        hyperparameters.put("tuning_cv_rmse",     tuning.bestScore());

        com.zuqi.domain.ai.AIModelRegistry registry = modelRegistry.registerModel(
                MODEL_NAME, "xgboost_regression", hyperparameters, "training_pipeline");

        Map<String, Object> metrics = new HashMap<>();
        metrics.put("r2",            eval.r2());
        metrics.put("mae",           eval.mae());
        metrics.put("rmse",          eval.rmse());
        metrics.put("training_size", trainingSize);

        modelRegistry.updateModelAfterTraining(registry.getId(), metrics, modelBytes,
                Map.of("feature_count", featureBuilder.getFeatureCount()));

        modelRegistry.promoteToActive(registry.getId());
        return registry.getId();
    }

    public record TrainingResult(
            boolean success,
            double  r2,
            double  mae,
            double  rmse,
            UUID    modelId,
            String  errorMessage
    ) {}
}
