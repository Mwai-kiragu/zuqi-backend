package com.zuqi.ai.crm;

import com.zuqi.ai.model.ModelRegistry;
import com.zuqi.ai.pipeline.ModelEvaluator;
import com.zuqi.ai.synthetic.SyntheticDataBundle;
import com.zuqi.ai.synthetic.SyntheticDataConfig;
import com.zuqi.ai.synthetic.SyntheticDataOrchestrator;
import com.zuqi.ai.synthetic.dto.SyntheticMerchant;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Training pipeline for Customer Lifetime Value (CLV) prediction.
 *
 * <p>Uses {@code revenue12m} as the prediction target.
 * Training target is set to the synthetic merchant's historical 12-month revenue,
 * with features computed from the same bundle as a training proxy.
 *
 * <p>Quality gate: RMSE &lt; 50 000 KES.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ClvTrainingPipeline {

    public static final String MODEL_NAME = "customer_clv_predictor";
    private static final double RMSE_GATE = 50_000.0;

    private final SyntheticDataOrchestrator orchestrator;
    private final SyntheticCustomerAnalyticsFeatureBuilder featureBuilder;
    private final ClvFeatureBuilder clvFeatureBuilder;
    private final ModelEvaluator modelEvaluator;
    private final ModelRegistry modelRegistry;
    private final Trainer<Regressor> xgBoostRegressionTrainer;

    @Transactional
    public TrainingResult runPipeline() {
        log.info("=== Starting CLV Training Pipeline ===");
        long start = System.currentTimeMillis();

        try {
            SyntheticDataConfig config = SyntheticDataConfig.defaultConfig(null, 123L);
            SyntheticDataBundle bundle = orchestrator.generateBundle(config);
            List<SyntheticMerchant> merchants = bundle.getMerchants();
            LocalDateTime asOf = LocalDateTime.now();

            // Build labelled examples: features → revenue12m as target
            List<ClvFeatureBuilder.LabelledClvExample> examples = new ArrayList<>();
            for (SyntheticMerchant m : merchants) {
                CustomerAnalyticsFeatures f = featureBuilder.computeFeatures(m, bundle, asOf);
                // Use revenue12m from features as target (proxy)
                double target = Math.max(0.0, f.revenue12m());
                examples.add(new ClvFeatureBuilder.LabelledClvExample(f, target));
            }

            // Split 80/20
            int trainSize = (int) (examples.size() * 0.8);
            List<ClvFeatureBuilder.LabelledClvExample> trainExamples = examples.subList(0, trainSize);
            List<ClvFeatureBuilder.LabelledClvExample> testExamples = examples.subList(trainSize, examples.size());

            Dataset<Regressor> trainDataset = clvFeatureBuilder.buildDataset(trainExamples);
            Dataset<Regressor> testDataset = clvFeatureBuilder.buildDataset(testExamples);

            Model<Regressor> model = xgBoostRegressionTrainer.train(trainDataset);
            log.info("CLV training complete on {} examples", trainSize);

            ModelEvaluator.RegressorEvaluationResult eval =
                    modelEvaluator.evaluateRegressor(model, testDataset);

            boolean passed = eval.rmse() < RMSE_GATE;
            log.info("{} RMSE={} (gate={})", passed ? "PASSED" : "FAILED",
                    String.format("%.2f", eval.rmse()), RMSE_GATE);

            if (!passed) {
                return new TrainingResult(false, eval.rmse(), null, "Failed RMSE gate");
            }

            UUID modelId = promoteModel(model, eval, trainSize);
            long duration = System.currentTimeMillis() - start;
            log.info("=== CLV pipeline complete in {}ms, modelId={} ===", duration, modelId);
            return new TrainingResult(true, eval.rmse(), modelId, null);

        } catch (Exception e) {
            log.error("CLV training pipeline failed: {}", e.getMessage(), e);
            return new TrainingResult(false, -1.0, null, e.getMessage());
        }
    }

    private UUID promoteModel(Model<Regressor> model,
                               ModelEvaluator.RegressorEvaluationResult eval,
                               int trainSize) throws Exception {
        byte[] modelBytes;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(model);
            modelBytes = baos.toByteArray();
        }

        Map<String, Object> hyperparameters = new HashMap<>();
        hyperparameters.put("algorithm", "xgboost_regression");
        hyperparameters.put("target", "revenue_12m");

        com.zuqi.domain.ai.AIModelRegistry registry = modelRegistry.registerModel(
                MODEL_NAME, "xgboost_regression", hyperparameters, "training_pipeline");

        Map<String, Object> metrics = new HashMap<>();
        metrics.put("rmse", eval.rmse());
        metrics.put("mae", eval.mae());
        metrics.put("r2", eval.r2());
        metrics.put("training_size", trainSize);

        modelRegistry.updateModelAfterTraining(registry.getId(), metrics, modelBytes,
                Map.of("feature_count", clvFeatureBuilder.getFeatureCount()));
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
