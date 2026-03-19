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
import org.tribuo.classification.Label;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Training pipeline for churn prediction (binary XGBoost classification).
 *
 * <p>Customers are labelled as churned if {@code daysSinceLastOrder > 60}.
 * Quality gate: AUC-ROC &ge; 0.70.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChurnTrainingPipeline {

    public static final String MODEL_NAME = "churn_predictor";
    private static final double AUC_GATE = 0.70;
    private static final int CHURN_THRESHOLD_DAYS = 60;

    private final SyntheticDataOrchestrator orchestrator;
    private final SyntheticCustomerAnalyticsFeatureBuilder featureBuilder;
    private final ChurnFeatureBuilder churnFeatureBuilder;
    private final ModelEvaluator modelEvaluator;
    private final ModelRegistry modelRegistry;
    private final Trainer<Label> xgBoostClassificationTrainer;

    @Transactional
    public TrainingResult runPipeline() {
        log.info("=== Starting Churn Prediction Training Pipeline ===");
        long start = System.currentTimeMillis();

        try {
            SyntheticDataConfig config = SyntheticDataConfig.defaultConfig(null, 77L);
            SyntheticDataBundle bundle = orchestrator.generateBundle(config);
            List<SyntheticMerchant> merchants = bundle.getMerchants();
            LocalDateTime asOf = LocalDateTime.now();

            // Build labelled examples
            List<ChurnFeatureBuilder.LabelledChurnExample> examples = new ArrayList<>();
            for (SyntheticMerchant m : merchants) {
                CustomerAnalyticsFeatures f = featureBuilder.computeFeatures(m, bundle, asOf);
                boolean churned = f.daysSinceLastOrder() > CHURN_THRESHOLD_DAYS;
                examples.add(new ChurnFeatureBuilder.LabelledChurnExample(f, churned));
            }

            // Split 80/20
            int trainSize = (int) (examples.size() * 0.8);
            List<ChurnFeatureBuilder.LabelledChurnExample> trainExamples = examples.subList(0, trainSize);
            List<ChurnFeatureBuilder.LabelledChurnExample> testExamples = examples.subList(trainSize, examples.size());

            Dataset<Label> trainDataset = churnFeatureBuilder.buildDataset(trainExamples);
            Dataset<Label> testDataset = churnFeatureBuilder.buildDataset(testExamples);

            Model<Label> model = xgBoostClassificationTrainer.train(trainDataset);
            log.info("Churn model training complete on {} examples", trainSize);

            ModelEvaluator.ClassifierEvaluationResult eval =
                    modelEvaluator.evaluateClassifier(model, testDataset, ChurnFeatureBuilder.LABEL_CHURNED);

            boolean passed = eval.aucRoc() >= AUC_GATE;
            log.info("{} AUC={} (gate={})", passed ? "PASSED" : "FAILED",
                    String.format("%.4f", eval.aucRoc()), AUC_GATE);

            if (!passed) {
                return new TrainingResult(false, eval.aucRoc(), null, "Failed AUC gate");
            }

            UUID modelId = promoteModel(model, eval, trainSize);
            long duration = System.currentTimeMillis() - start;
            log.info("=== Churn pipeline complete in {}ms, modelId={} ===", duration, modelId);
            return new TrainingResult(true, eval.aucRoc(), modelId, null);

        } catch (Exception e) {
            log.error("Churn training pipeline failed: {}", e.getMessage(), e);
            return new TrainingResult(false, -1.0, null, e.getMessage());
        }
    }

    private UUID promoteModel(Model<Label> model,
                               ModelEvaluator.ClassifierEvaluationResult eval,
                               int trainSize) throws Exception {
        byte[] modelBytes;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(model);
            modelBytes = baos.toByteArray();
        }

        Map<String, Object> hyperparameters = new HashMap<>();
        hyperparameters.put("algorithm", "xgboost_classification");
        hyperparameters.put("churn_threshold_days", CHURN_THRESHOLD_DAYS);

        com.zuqi.domain.ai.AIModelRegistry registry = modelRegistry.registerModel(
                MODEL_NAME, "xgboost_classification", hyperparameters, "training_pipeline");

        Map<String, Object> metrics = new HashMap<>();
        metrics.put("auc_roc", eval.aucRoc());
        metrics.put("accuracy", eval.accuracy());
        metrics.put("precision", eval.precision());
        metrics.put("recall", eval.recall());
        metrics.put("f1", eval.f1Score());
        metrics.put("training_size", trainSize);

        modelRegistry.updateModelAfterTraining(registry.getId(), metrics, modelBytes,
                Map.of("feature_count", churnFeatureBuilder.getFeatureCount()));
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
