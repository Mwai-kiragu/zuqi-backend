package com.zuqi.ai.demand;

import com.zuqi.ai.feature.DemandFeatures;
import com.zuqi.ai.feature.OrderFeatureService;
import com.zuqi.ai.model.ModelRegistry;
import com.zuqi.ai.pipeline.ModelEvaluator;
import com.zuqi.domain.ai.AIModelRegistry;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tribuo.Dataset;
import org.tribuo.Model;
import org.tribuo.regression.Regressor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Orchestrates end-to-end ML training pipeline for demand forecasting models.
 *
 * Pipeline flow:
 * 1. Generate synthetic order sequences
 * 2. Build demand features from sequences
 * 3. Split into train/test (80/20)
 * 4. Train XGBoost regressor
 * 5. Evaluate model (quality gate: R² > 0.70)
 * 6. Promote model to ACTIVE if quality gates pass
 *
 * Blueprint: plan.md Section 6.2 - Demand Forecasting Module
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DemandModelTrainingPipeline {

    private final SyntheticOrderDataGenerator syntheticDataGenerator;
    private final DemandModelTrainer modelTrainer;
    private final ModelEvaluator modelEvaluator;
    private final DemandFeatureBuilder featureBuilder;
    private final OrderFeatureService orderFeatureService;
    private final ModelRegistry modelRegistry;

    /**
     * Run complete training pipeline for demand forecasting model.
     *
     * @param numMerchants Number of synthetic merchants
     * @param numProducts Number of synthetic products
     * @param numWeeks Number of weeks of historical data
     * @return Pipeline result with model evaluation
     */
    @Transactional
    public TrainingPipelineResult runPipeline(int numMerchants, int numProducts, int numWeeks) {
        log.info("=".repeat(80));
        log.info("Starting Demand Forecasting Model Training Pipeline");
        log.info("Merchants: {}, Products: {}, Weeks: {}", numMerchants, numProducts, numWeeks);
        log.info("=".repeat(80));

        long pipelineStartTime = System.currentTimeMillis();

        try {
            // Step 1: Generate synthetic order sequences
            log.info("Step 1/6: Generating synthetic order sequences...");
            List<SyntheticOrderDataGenerator.SyntheticOrderSequence> sequences =
                    syntheticDataGenerator.generateOrderSequences(numMerchants, numProducts, numWeeks);

            log.info("✅ Step 1 complete: Generated {} order sequences", sequences.size());

            // Step 2: Build training examples from sequences
            log.info("Step 2/6: Building training examples from order sequences...");
            List<DemandModelTrainer.DemandTrainingExample> trainingData =
                    buildTrainingExamples(sequences);

            log.info("✅ Step 2 complete: Built {} training examples", trainingData.size());

            // Step 3: Split into train/test (80/20)
            log.info("Step 3/6: Splitting data into train/test sets (80/20)...");
            int trainSize = (int) (trainingData.size() * 0.8);
            List<DemandModelTrainer.DemandTrainingExample> trainData =
                    trainingData.subList(0, trainSize);
            List<DemandModelTrainer.DemandTrainingExample> testData =
                    trainingData.subList(trainSize, trainingData.size());

            log.info("✅ Step 3 complete: Train={} examples, Test={} examples",
                    trainData.size(), testData.size());

            // Step 4: Train XGBoost regressor
            log.info("Step 4/6: Training XGBoost demand forecasting model...");
            modelTrainer.validateDataset(trainData);
            Model<Regressor> model = modelTrainer.train(trainData);
            log.info("✅ Step 4 complete: Model trained");

            // Step 5: Evaluate model
            log.info("Step 5/6: Evaluating demand forecasting model...");
            Dataset<Regressor> testDataset = buildTestDataset(testData);
            ModelEvaluator.RegressorEvaluationResult evaluation =
                    modelEvaluator.evaluateRegressor(model, testDataset);

            if (evaluation.passedQualityGate()) {
                log.info("✅ Step 5 complete: Model evaluation PASSED quality gate");
            } else {
                log.warn("⚠️ Step 5 complete: Model evaluation FAILED quality gate");
            }

            // Step 6: Promote model to ACTIVE if quality gate passes
            log.info("Step 6/6: Promoting model to production...");
            UUID modelId = null;

            if (evaluation.passedQualityGate()) {
                modelId = promoteModel(model, evaluation, trainData.size());
                log.info("✅ Model promoted to ACTIVE: {}", modelId);
            } else {
                log.warn("❌ Model NOT promoted (failed quality gate)");
            }

            log.info("✅ Step 6 complete: Model promotion finished");

            long pipelineDuration = System.currentTimeMillis() - pipelineStartTime;

            log.info("=".repeat(80));
            log.info("Training Pipeline Completed Successfully in {}ms", pipelineDuration);
            log.info("Model: {} (R²={:.3f})",
                    evaluation.passedQualityGate() ? "PROMOTED" : "NOT PROMOTED",
                    evaluation.r2());
            log.info("=".repeat(80));

            return TrainingPipelineResult.builder()
                    .success(true)
                    .numSequences(sequences.size())
                    .numTrainingExamples(trainingData.size())
                    .trainSize(trainData.size())
                    .testSize(testData.size())
                    .evaluation(evaluation)
                    .modelId(modelId)
                    .durationMs(pipelineDuration)
                    .build();

        } catch (Exception e) {
            long pipelineDuration = System.currentTimeMillis() - pipelineStartTime;
            log.error("Training pipeline failed after {}ms: {}", pipelineDuration, e.getMessage(), e);

            return TrainingPipelineResult.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .durationMs(pipelineDuration)
                    .build();
        }
    }

    /**
     * Build training examples from synthetic order sequences.
     *
     * For each sequence, create training examples by:
     * 1. Taking historical data up to week N
     * 2. Computing demand features as of week N
     * 3. Using week N+1 quantity as target
     */
    private List<DemandModelTrainer.DemandTrainingExample> buildTrainingExamples(
            List<SyntheticOrderDataGenerator.SyntheticOrderSequence> sequences) {

        List<DemandModelTrainer.DemandTrainingExample> examples = new ArrayList<>();

        for (var sequence : sequences) {
            List<SyntheticOrderDataGenerator.WeeklyOrder> weeklyOrders = sequence.weeklyOrders();

            // Need at least 5 weeks of history to compute features (4 lag weeks + current week)
            if (weeklyOrders.size() < 6) {
                continue;
            }

            // Create training examples from week 5 onwards (need 4 weeks history)
            for (int i = 4; i < weeklyOrders.size() - 1; i++) {
                LocalDate asOfDate = weeklyOrders.get(i).weekStart();
                LocalDate targetDate = weeklyOrders.get(i + 1).weekStart();
                BigDecimal targetQuantity = weeklyOrders.get(i + 1).quantity();

                // Build features using data up to asOfDate
                DemandFeatures features = buildFeaturesFromSequence(
                        sequence,
                        weeklyOrders.subList(0, i + 1),
                        asOfDate
                );

                examples.add(new DemandModelTrainer.DemandTrainingExample(
                        features,
                        targetQuantity
                ));
            }
        }

        log.info("Built {} training examples from {} sequences", examples.size(), sequences.size());
        return examples;
    }

    /**
     * Build demand features from synthetic order sequence.
     */
    private DemandFeatures buildFeaturesFromSequence(
            SyntheticOrderDataGenerator.SyntheticOrderSequence sequence,
            List<SyntheticOrderDataGenerator.WeeklyOrder> historicalOrders,
            LocalDate asOfDate) {

        // Compute lag features (last 4 weeks)
        BigDecimal qty1wAgo = getQuantity(historicalOrders, historicalOrders.size() - 1);
        BigDecimal qty2wAgo = getQuantity(historicalOrders, historicalOrders.size() - 2);
        BigDecimal qty3wAgo = getQuantity(historicalOrders, historicalOrders.size() - 3);
        BigDecimal qty4wAgo = getQuantity(historicalOrders, historicalOrders.size() - 4);

        // Compute rolling averages
        BigDecimal rollingAvg4w = computeAverage(historicalOrders, 4);
        BigDecimal rollingAvg12w = computeAverage(historicalOrders, 12);

        // Compute trend direction
        String trendDirection = computeTrendDirection(rollingAvg4w, rollingAvg12w);

        // Temporal features
        int dayOfWeek = asOfDate.getDayOfWeek().getValue();
        int weekOfMonth = (asOfDate.getDayOfMonth() - 1) / 7 + 1;
        int monthOfYear = asOfDate.getMonthValue();

        // Randomized merchant tenure (30 days to 3 years)
        int merchantTenureDays = 30 + new Random(sequence.merchantId().hashCode()).nextInt(365 * 3);

        return DemandFeatures.builder()
                .merchantId(sequence.merchantId())
                .productId(sequence.productId())
                .computedAt(asOfDate.atStartOfDay())
                // Lag features
                .qty1wAgo(qty1wAgo)
                .qty2wAgo(qty2wAgo)
                .qty3wAgo(qty3wAgo)
                .qty4wAgo(qty4wAgo)
                .rollingAvg4w(rollingAvg4w)
                .rollingAvg12w(rollingAvg12w)
                .trendDirection(trendDirection)
                // Temporal features
                .dayOfWeek(dayOfWeek)
                .weekOfMonth(weekOfMonth)
                .monthOfYear(monthOfYear)
                .isHoliday(false) // Simplified for synthetic data
                .isPaydayWeek(isPaydayWeek(asOfDate))
                .isRamadan(isRamadanPeriod(asOfDate))
                .isChristmasSeason(monthOfYear >= 11)
                // Merchant context
                .merchantCategory(sequence.merchantCategory())
                .merchantSizeTier(sequence.merchantSizeTier())
                .merchantCreditStatus("GOOD") // Simplified for synthetic data
                .merchantTenureDays(merchantTenureDays)
                // SKU context
                .productCategory(sequence.productCategory())
                .priceTier(sequence.priceTier())
                .isPromotional(false) // Simplified for synthetic data
                .typicalShelfLifeDays(365) // Simplified for synthetic data
                .build();
    }

    /**
     * Get quantity from historical orders at specific index.
     */
    private BigDecimal getQuantity(List<SyntheticOrderDataGenerator.WeeklyOrder> orders, int index) {
        if (index < 0 || index >= orders.size()) {
            return BigDecimal.ZERO;
        }
        return orders.get(index).quantity();
    }

    /**
     * Compute rolling average over last N weeks.
     */
    private BigDecimal computeAverage(List<SyntheticOrderDataGenerator.WeeklyOrder> orders, int weeks) {
        int startIdx = Math.max(0, orders.size() - weeks);
        List<SyntheticOrderDataGenerator.WeeklyOrder> recent = orders.subList(startIdx, orders.size());

        if (recent.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal sum = recent.stream()
                .map(SyntheticOrderDataGenerator.WeeklyOrder::quantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return sum.divide(BigDecimal.valueOf(recent.size()), 2, java.math.RoundingMode.HALF_UP);
    }

    /**
     * Compute trend direction by comparing short-term and long-term averages.
     */
    private String computeTrendDirection(BigDecimal avg4w, BigDecimal avg12w) {
        if (avg4w == null || avg12w == null ||
            avg4w.compareTo(BigDecimal.ZERO) == 0 ||
            avg12w.compareTo(BigDecimal.ZERO) == 0) {
            return "STABLE";
        }

        double ratio = avg4w.divide(avg12w, 4, java.math.RoundingMode.HALF_UP).doubleValue();

        if (ratio > 1.10) return "INCREASING"; // 4w avg > 10% higher than 12w avg
        if (ratio < 0.90) return "DECREASING"; // 4w avg > 10% lower than 12w avg
        return "STABLE";
    }

    /**
     * Check if date is payday week (28th-5th).
     */
    private boolean isPaydayWeek(LocalDate date) {
        int day = date.getDayOfMonth();
        return day >= 28 || day <= 5;
    }

    /**
     * Check if date falls during Ramadan (approximate).
     */
    private boolean isRamadanPeriod(LocalDate date) {
        int year = date.getYear();
        int month = date.getMonthValue();
        int day = date.getDayOfMonth();

        if (year == 2025) {
            return month == 3; // March
        } else if (year == 2026) {
            return (month == 2 && day >= 18) || (month == 3 && day <= 19);
        }

        return false;
    }

    /**
     * Build Tribuo test dataset from training examples.
     */
    private Dataset<Regressor> buildTestDataset(
            List<DemandModelTrainer.DemandTrainingExample> testData) {

        List<DemandFeatures> features = testData.stream()
                .map(DemandModelTrainer.DemandTrainingExample::features)
                .toList();

        List<BigDecimal> quantities = testData.stream()
                .map(DemandModelTrainer.DemandTrainingExample::actualQuantity)
                .toList();

        return featureBuilder.buildRegressionDataset(features, quantities);
    }

    /**
     * Promote model to ACTIVE in model registry.
     */
    private UUID promoteModel(Model<Regressor> model,
                               ModelEvaluator.RegressorEvaluationResult eval,
                               int trainingSize) {
        String modelName = "demand_forecaster";

        try {
            // 1. Register model in TRAINING status
            Map<String, Object> hyperparameters = Map.of(
                    "algorithm", "xgboost_regression",
                    "num_rounds", 150,
                    "max_depth", 5,
                    "eta", 0.05,
                    "subsample", 0.8,
                    "colsample_bytree", 0.8,
                    "min_child_weight", 5,
                    "gamma", 0.0
            );

            AIModelRegistry registry = modelRegistry.registerModel(
                    modelName,
                    "xgboost_regression",
                    hyperparameters,
                    "training_pipeline"
            );

            // 2. Serialize model
            byte[] modelBinary = serializeModel(model);

            // 3. Update with training results
            Map<String, Object> performanceMetrics = Map.of(
                    "rmse", eval.rmse(),
                    "mae", eval.mae(),
                    "r2", eval.r2(),
                    "explained_variance", eval.explainedVariance(),
                    "training_samples", trainingSize
            );

            Map<String, Object> featureColumns = Map.of(
                    "feature_names", featureBuilder.getFeatureNames(),
                    "feature_count", featureBuilder.getFeatureCount()
            );

            modelRegistry.updateModelAfterTraining(
                    registry.getId(),
                    performanceMetrics,
                    modelBinary,
                    featureColumns
            );

            // 4. Promote to ACTIVE if passed quality gate
            modelRegistry.promoteToActive(registry.getId());

            log.info("Successfully promoted demand forecasting model to ACTIVE: {} v{} (R²={:.3f})",
                    modelName, registry.getModelVersion(), eval.r2());

            return registry.getId();

        } catch (Exception e) {
            log.error("Failed to promote demand forecasting model: {}", e.getMessage(), e);
            throw new RuntimeException("Model promotion failed", e);
        }
    }

    /**
     * Serialize Tribuo model to byte array.
     */
    private byte[] serializeModel(Model<?> model) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(model);
            oos.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            log.error("Failed to serialize model: {}", e.getMessage(), e);
            throw new RuntimeException("Model serialization failed", e);
        }
    }

    /**
     * Training pipeline result.
     */
    @Builder
    public record TrainingPipelineResult(
            boolean success,
            String errorMessage,
            int numSequences,
            int numTrainingExamples,
            int trainSize,
            int testSize,
            ModelEvaluator.RegressorEvaluationResult evaluation,
            UUID modelId,
            long durationMs
    ) {
    }
}
