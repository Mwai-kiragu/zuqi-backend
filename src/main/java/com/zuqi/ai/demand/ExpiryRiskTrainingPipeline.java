package com.zuqi.ai.demand;

import com.zuqi.ai.feature.ExpiryFeatures;
import com.zuqi.ai.model.ModelRegistry;
import com.zuqi.ai.pipeline.ModelEvaluator;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Training pipeline for expiry risk prediction model (Model #10).
 *
 * Pipeline:
 * 1. Generate synthetic batches (500)
 * 2. Build feature dataset
 * 3. Split 80/20
 * 4. Train XGBoost regressor
 * 5. Evaluate RMSE (gate: RMSE < 0.20 on probability scale)
 * 6. Compute residual percentiles
 * 7. Promote to ACTIVE
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExpiryRiskTrainingPipeline {

    private final SyntheticExpiryBatchGenerator batchGenerator;
    private final ExpiryRiskFeatureBuilder featureBuilder;
    private final ModelEvaluator modelEvaluator;
    private final ModelRegistry modelRegistry;
    private final Trainer<Regressor> xgBoostRegressionTrainer;

    static final String MODEL_NAME = "expiry_risk_predictor";
    private static final double RMSE_GATE = 0.20;

    @Transactional
    public TrainingResult runPipeline() {
        log.info("=== Starting Expiry Risk Training Pipeline ===");
        long start = System.currentTimeMillis();

        try {
            // Step 1: Generate synthetic batches
            List<SyntheticExpiryBatchGenerator.SyntheticExpiryBatch> batches =
                    batchGenerator.generateBatches(500);

            // Step 2: Build feature examples
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

            // Step 3: Split 80/20
            int trainSize = (int) (examples.size() * 0.8);
            List<ExpiryRiskFeatureBuilder.LabelledExpiryExample> trainExamples =
                    examples.subList(0, trainSize);
            List<ExpiryRiskFeatureBuilder.LabelledExpiryExample> testExamples =
                    examples.subList(trainSize, examples.size());

            // Step 4: Train
            Dataset<Regressor> trainDataset = featureBuilder.buildDataset(trainExamples);
            Model<Regressor> model = xgBoostRegressionTrainer.train(trainDataset);
            log.info("Training complete on {} examples", trainSize);

            // Step 5: Evaluate
            Dataset<Regressor> testDataset = featureBuilder.buildDataset(testExamples);
            ModelEvaluator.RegressorEvaluationResult eval =
                    modelEvaluator.evaluateRegressor(model, testDataset);

            boolean passed = eval.rmse() < RMSE_GATE;
            log.info("{} RMSE={} (gate={})",
                    passed ? "PASSED" : "FAILED", String.format("%.4f", eval.rmse()), RMSE_GATE);

            if (!passed) {
                return new TrainingResult(false, eval.rmse(), null, "Failed RMSE gate");
            }

            // Step 6: Compute residual percentiles
            double[] residuals = computeResidualPercentiles(model, testExamples);

            // Step 7: Promote
            UUID modelId = promoteModel(model, eval, trainSize, residuals);

            long duration = System.currentTimeMillis() - start;
            log.info("=== Expiry Risk Pipeline complete in {}ms, modelId={} ===", duration, modelId);
            return new TrainingResult(true, eval.rmse(), modelId, null);

        } catch (Exception e) {
            log.error("Expiry risk training pipeline failed: {}", e.getMessage(), e);
            return new TrainingResult(false, -1.0, null, e.getMessage());
        }
    }

    private double[] computeResidualPercentiles(
            Model<Regressor> model,
            List<ExpiryRiskFeatureBuilder.LabelledExpiryExample> testExamples) {

        List<Double> residuals = new ArrayList<>();
        for (var le : testExamples) {
            var ex = featureBuilder.buildExample(le.features());
            double predicted = model.predict(ex).getOutput().getValues()[0];
            residuals.add(le.sellThroughProbability() - predicted);
        }
        residuals.sort(Double::compareTo);
        int p10 = (int) (residuals.size() * 0.10);
        int p90 = Math.min((int) (residuals.size() * 0.90), residuals.size() - 1);
        return new double[]{residuals.get(p10), residuals.get(p90)};
    }

    private UUID promoteModel(Model<Regressor> model,
                               ModelEvaluator.RegressorEvaluationResult eval,
                               int trainingSize,
                               double[] residuals) throws Exception {

        byte[] modelBytes;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(model);
            modelBytes = baos.toByteArray();
        }

        Map<String, Object> hyperparameters = new HashMap<>();
        hyperparameters.put("algorithm", "xgboost_regression");
        hyperparameters.put("num_rounds", 50);

        com.zuqi.domain.ai.AIModelRegistry registry = modelRegistry.registerModel(
                MODEL_NAME, "xgboost_regression", hyperparameters, "training_pipeline");

        Map<String, Object> metrics = new HashMap<>();
        metrics.put("rmse", eval.rmse());
        metrics.put("r2", eval.r2());
        metrics.put("mae", eval.mae());
        metrics.put("training_size", trainingSize);
        metrics.put("lower_residual", residuals[0]);
        metrics.put("upper_residual", residuals[1]);

        modelRegistry.updateModelAfterTraining(registry.getId(), metrics, modelBytes,
                java.util.Map.of("feature_count", 8));

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
