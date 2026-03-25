package com.zuqi.ai.crm;

import com.zuqi.ai.model.ModelRegistry;
import com.zuqi.ai.pipeline.ModelEvaluator;
import com.zuqi.ai.pipeline.XGBoostHyperparameterTuner;
import com.zuqi.ai.synthetic.SyntheticDataBundle;
import com.zuqi.ai.synthetic.SyntheticDataConfig;
import com.zuqi.ai.synthetic.SyntheticDataOrchestrator;
import com.zuqi.ai.synthetic.dto.SyntheticMerchant;
import com.zuqi.ai.synthetic.dto.SyntheticOrder;
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
import java.util.Random;
import java.util.UUID;

/**
 * Training pipeline for the visit frequency optimizer.
 *
 * <p>Generates positive examples (day-of-week when an order occurred) and negative examples
 * (random other day) from synthetic merchant order history, then trains an XGBoost regressor
 * to predict order-conversion probability per day.
 *
 * <p>Quality gate: RMSE &lt; 0.40.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VisitTrainingPipeline {

    public static final String MODEL_NAME = "visit_optimizer";
    private static final double RMSE_GATE = 0.50;

    private final SyntheticDataOrchestrator orchestrator;
    private final SyntheticCustomerAnalyticsFeatureBuilder featureBuilder;
    private final VisitFeatureBuilder visitFeatureBuilder;
    private final ModelEvaluator modelEvaluator;
    private final ModelRegistry modelRegistry;
    private final Trainer<Regressor> xgBoostRegressionTrainer;
    private final XGBoostHyperparameterTuner hyperparameterTuner;

    @Transactional
    public TrainingResult runPipeline() {
        log.info("=== Starting Visit Optimizer Training Pipeline ===");
        long start = System.currentTimeMillis();
        Random rng = new Random(99L);

        try {
            SyntheticDataConfig config = SyntheticDataConfig.defaultConfig(null, 99L);
            SyntheticDataBundle bundle = orchestrator.generateBundle(config);
            List<SyntheticMerchant> merchants = bundle.getMerchants();
            LocalDateTime asOf = LocalDateTime.now();

            List<VisitFeatureBuilder.LabelledVisitExample> examples = new ArrayList<>();

            for (SyntheticMerchant m : merchants) {
                CustomerAnalyticsFeatures f = featureBuilder.computeFeatures(m, bundle, asOf);
                List<SyntheticOrder> orders = bundle.getOrdersForMerchant(m.syntheticId());

                // Count historical orders per day-of-week for this merchant
                Map<Integer, Long> ordersByDow = new HashMap<>();
                for (SyntheticOrder o : orders) {
                    if (o.orderDate() == null) continue;
                    int dow = o.orderDate().getDayOfWeek().getValue();
                    ordersByDow.merge(dow, 1L, (a, b) -> a + b);
                }

                for (SyntheticOrder order : orders) {
                    if (order.orderDate() == null) continue;
                    int orderDayOfWeek = order.orderDate().getDayOfWeek().getValue(); // 1=Mon…7=Sun
                    double countOnThisDay = ordersByDow.getOrDefault(orderDayOfWeek, 0L).doubleValue();

                    // Positive example: actual order day
                    examples.add(new VisitFeatureBuilder.LabelledVisitExample(
                            f, orderDayOfWeek, countOnThisDay, false, false, 1.0));

                    // Negative example: random other day with its own historical count
                    int negativeDayOfWeek;
                    do {
                        negativeDayOfWeek = 1 + rng.nextInt(7);
                    } while (negativeDayOfWeek == orderDayOfWeek);
                    double countOnNegDay = ordersByDow.getOrDefault(negativeDayOfWeek, 0L).doubleValue();

                    examples.add(new VisitFeatureBuilder.LabelledVisitExample(
                            f, negativeDayOfWeek, countOnNegDay, false, false, 0.0));
                }
            }

            if (examples.isEmpty()) {
                return new TrainingResult(false, -1.0, null, "No training examples generated");
            }

            // Split 80/20
            int trainSize = (int) (examples.size() * 0.8);
            List<VisitFeatureBuilder.LabelledVisitExample> trainExamples = examples.subList(0, trainSize);
            List<VisitFeatureBuilder.LabelledVisitExample> testExamples = examples.subList(trainSize, examples.size());

            Dataset<Regressor> trainDataset = visitFeatureBuilder.buildDataset(trainExamples);
            Dataset<Regressor> testDataset = visitFeatureBuilder.buildDataset(testExamples);

            XGBoostHyperparameterTuner.TunedModel<Regressor> tunedModel =
                    hyperparameterTuner.tuneAndTrainRegressor(trainDataset);
            Model<Regressor> model = tunedModel.model();
            XGBoostHyperparameterTuner.TuningResult tuning = tunedModel.tuning();
            log.info("Visit model training complete on {} examples (rounds={} eta={} maxDepth={})",
                    trainSize, tuning.bestNumRounds(), tuning.bestEta(), tuning.bestMaxDepth());

            ModelEvaluator.RegressorEvaluationResult eval =
                    modelEvaluator.evaluateRegressor(model, testDataset);

            boolean passed = eval.rmse() < RMSE_GATE;
            log.info("{} RMSE={} (gate={})", passed ? "PASSED" : "FAILED",
                    String.format("%.4f", eval.rmse()), RMSE_GATE);

            if (!passed) {
                return new TrainingResult(false, eval.rmse(), null, "Failed RMSE gate");
            }

            UUID modelId = promoteModel(model, eval, trainSize, tuning);
            long duration = System.currentTimeMillis() - start;
            log.info("=== Visit pipeline complete in {}ms, modelId={} ===", duration, modelId);
            return new TrainingResult(true, eval.rmse(), modelId, null);

        } catch (Exception e) {
            log.error("Visit training pipeline failed: {}", e.getMessage(), e);
            return new TrainingResult(false, -1.0, null, e.getMessage());
        }
    }

    private UUID promoteModel(Model<Regressor> model,
                               ModelEvaluator.RegressorEvaluationResult eval,
                               int trainSize,
                               XGBoostHyperparameterTuner.TuningResult tuning) throws Exception {
        byte[] modelBytes;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(model);
            modelBytes = baos.toByteArray();
        }

        Map<String, Object> hyperparameters = new HashMap<>();
        hyperparameters.put("algorithm", "xgboost_regression");
        hyperparameters.put("target", "order_conversion");
        hyperparameters.put("tuned_num_rounds", tuning.bestNumRounds());
        hyperparameters.put("tuned_eta", tuning.bestEta());
        hyperparameters.put("tuned_max_depth", tuning.bestMaxDepth());
        hyperparameters.put("tuning_cv_rmse", tuning.bestScore());

        com.zuqi.domain.ai.AIModelRegistry registry = modelRegistry.registerModel(
                MODEL_NAME, "xgboost_regression", hyperparameters, "training_pipeline");

        Map<String, Object> metrics = new HashMap<>();
        metrics.put("rmse", eval.rmse());
        metrics.put("r2", eval.r2());
        metrics.put("training_size", trainSize);

        modelRegistry.updateModelAfterTraining(registry.getId(), metrics, modelBytes,
                Map.of("feature_count", visitFeatureBuilder.getFeatureCount()));
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
