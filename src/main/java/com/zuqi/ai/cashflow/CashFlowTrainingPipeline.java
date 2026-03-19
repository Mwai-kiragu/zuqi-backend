package com.zuqi.ai.cashflow;

import com.zuqi.ai.model.ModelRegistry;
import com.zuqi.ai.pipeline.ModelEvaluator;
import com.zuqi.ai.synthetic.SyntheticCashFlowFeatureBuilder;
import com.zuqi.ai.synthetic.SyntheticCashFlowFeatureBuilder.LabelledCashFlowExample;
import com.zuqi.ai.synthetic.generators.SyntheticCashFlowGenerator;
import com.zuqi.ai.synthetic.dto.SyntheticCashFlowSnapshot;
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
 * Training pipeline for the cash flow prediction model (Model #12).
 *
 * Pipeline:
 * 1. Generate synthetic cash flow snapshots (365 days × 5 distributor profiles)
 * 2. Build labelled feature examples
 * 3. Split 80/20
 * 4. Train XGBoost regressor
 * 5. Evaluate RMSE (gate: passes ModelEvaluator R² >= 0.70)
 * 6. Compute residual percentiles for prediction bounds
 * 7. Promote to ACTIVE
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CashFlowTrainingPipeline {

    public static final String MODEL_NAME = "cash_flow_predictor";
    private final SyntheticCashFlowGenerator syntheticGenerator;
    private final SyntheticCashFlowFeatureBuilder syntheticFeatureBuilder;
    private final CashFlowFeatureBuilder featureBuilder;
    private final ModelEvaluator modelEvaluator;
    private final ModelRegistry modelRegistry;
    private final Trainer<Regressor> xgBoostRegressionTrainer;

    @Transactional
    public TrainingResult runPipeline() {
        log.info("=== Starting Cash Flow Training Pipeline ===");
        long start = System.currentTimeMillis();

        try {
            // Step 1: Generate snapshots for multiple synthetic distributor profiles
            List<SyntheticCashFlowSnapshot> allSnapshots = new ArrayList<>();
            for (int profile = 0; profile < 5; profile++) {
                List<SyntheticCashFlowSnapshot> profileSnapshots =
                        syntheticGenerator.generate(List.of(), List.of(), 42L + profile);
                allSnapshots.addAll(profileSnapshots);
            }
            log.info("Generated {} total synthetic cash flow snapshots", allSnapshots.size());

            // Step 2: Build labelled examples
            List<LabelledCashFlowExample> examples =
                    syntheticFeatureBuilder.buildLabelledExamples(allSnapshots);

            if (examples.size() < 50) {
                return new TrainingResult(false, -1.0, -1.0, null,
                        "Insufficient training examples: " + examples.size());
            }

            // Step 3: Split 80/20
            int trainSize = (int) (examples.size() * 0.8);
            List<LabelledCashFlowExample> trainExamples = examples.subList(0, trainSize);
            List<LabelledCashFlowExample> testExamples = examples.subList(trainSize, examples.size());

            // Step 4: Train
            Dataset<Regressor> trainDataset = featureBuilder.buildDataset(trainExamples);
            Model<Regressor> model = xgBoostRegressionTrainer.train(trainDataset);
            log.info("Training complete on {} examples", trainSize);

            // Step 5: Evaluate
            Dataset<Regressor> testDataset = featureBuilder.buildDataset(testExamples);
            ModelEvaluator.RegressorEvaluationResult eval =
                    modelEvaluator.evaluateRegressor(model, testDataset);

            log.info("Evaluation: RMSE={} R²={} MAE={}",
                    String.format("%.2f", eval.rmse()),
                    String.format("%.4f", eval.r2()),
                    String.format("%.2f", eval.mae()));

            if (!eval.passedQualityGate()) {
                return new TrainingResult(false, eval.rmse(), eval.r2(), null,
                        "Failed R² quality gate: " + String.format("%.4f", eval.r2()));
            }

            // Step 6: Compute residual percentiles for prediction bounds
            double[] residuals = computeResidualPercentiles(model, testExamples);

            // Step 7: Promote
            UUID modelId = promoteModel(model, eval, trainSize, residuals);

            long duration = System.currentTimeMillis() - start;
            log.info("=== Cash Flow Pipeline complete in {}ms, modelId={} ===", duration, modelId);
            return new TrainingResult(true, eval.rmse(), eval.r2(), modelId, null);

        } catch (Exception e) {
            log.error("Cash flow training pipeline failed: {}", e.getMessage(), e);
            return new TrainingResult(false, -1.0, -1.0, null, e.getMessage());
        }
    }

    // ── Residual percentiles ─────────────────────────────────────────────────

    private double[] computeResidualPercentiles(Model<Regressor> model,
                                                  List<LabelledCashFlowExample> testExamples) {
        List<Double> residuals = new ArrayList<>();
        for (var le : testExamples) {
            var ex = featureBuilder.buildExample(le.features());
            double predicted = model.predict(ex).getOutput().getValues()[0];
            residuals.add(le.netCashFlow() - predicted);
        }
        residuals.sort(Double::compareTo);
        int p10 = (int) (residuals.size() * 0.10);
        int p90 = Math.min((int) (residuals.size() * 0.90), residuals.size() - 1);
        return new double[]{residuals.get(p10), residuals.get(p90)};
    }

    // ── Model promotion ───────────────────────────────────────────────────────

    private UUID promoteModel(Model<Regressor> model,
                               ModelEvaluator.RegressorEvaluationResult eval,
                               int trainSize,
                               double[] residuals) throws Exception {
        byte[] modelBytes;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(model);
            modelBytes = baos.toByteArray();
        }

        Map<String, Object> hyperparams = new HashMap<>();
        hyperparams.put("algorithm", "xgboost_regression");

        com.zuqi.domain.ai.AIModelRegistry registry = modelRegistry.registerModel(
                MODEL_NAME, "xgboost_regression", hyperparams, "training_pipeline");

        Map<String, Object> metrics = new HashMap<>();
        metrics.put("rmse", eval.rmse());
        metrics.put("mae", eval.mae());
        metrics.put("r2", eval.r2());
        metrics.put("training_size", trainSize);
        metrics.put("lower_residual", residuals[0]);
        metrics.put("upper_residual", residuals[1]);

        modelRegistry.updateModelAfterTraining(registry.getId(), metrics, modelBytes,
                Map.of("feature_count", CashFlowFeatureBuilder.FEATURE_COUNT));
        modelRegistry.promoteToActive(registry.getId());

        return registry.getId();
    }

    public record TrainingResult(
            boolean success,
            double rmse,
            double r2,
            UUID modelId,
            String errorMessage
    ) {}
}
