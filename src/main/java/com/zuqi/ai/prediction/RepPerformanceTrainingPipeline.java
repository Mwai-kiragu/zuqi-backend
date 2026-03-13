package com.zuqi.ai.prediction;

import com.zuqi.ai.feature.SalesRepFeatures;
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
import java.time.LocalDateTime;
import java.util.*;

/**
 * End-to-end training pipeline for sales rep performance predictor.
 *
 * Steps:
 * 1. Generate 400 synthetic rep × period snapshots with rule-based ground-truth scores
 * 2. 80/20 train/test split
 * 3. Train XGBoost regressor
 * 4. Evaluate: quality gate = R² ≥ 0.70
 * 5. Promote via ModelRegistry
 *
 * Blueprint reference: implementation_plan.md Phase 4, Step 7b
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RepPerformanceTrainingPipeline {

    private static final String MODEL_NAME     = RepPerformancePredictor.MODEL_NAME;
    private static final int    NUM_SNAPSHOTS  = 400;

    private final RepPerformanceModelTrainer  modelTrainer;
    private final RepPerformanceFeatureBuilder featureBuilder;
    private final ModelEvaluator              modelEvaluator;
    private final ModelRegistry               modelRegistry;

    @Transactional
    public TrainingPipelineResult runPipeline() {
        log.info("{}", "=".repeat(80));
        log.info("Starting Rep Performance Predictor Training Pipeline");
        log.info("{}", "=".repeat(80));

        long startTime = System.currentTimeMillis();

        try {
            // Steps 1-2
            log.info("Step 1/5: Generating {} synthetic rep snapshots…", NUM_SNAPSHOTS);
            List<SalesRepFeatures> snapshots = generateRepSnapshots(NUM_SNAPSHOTS);
            List<Double>           scores    = labelSnapshots(snapshots);

            int trainSize = (int) (snapshots.size() * 0.8);
            List<SalesRepFeatures> trainFeatures = snapshots.subList(0, trainSize);
            List<Double>           trainScores   = scores.subList(0, trainSize);
            List<SalesRepFeatures> testFeatures  = snapshots.subList(trainSize, snapshots.size());
            List<Double>           testScores    = scores.subList(trainSize, scores.size());

            log.info("Split: train={} test={}", trainSize, testFeatures.size());

            // Step 3
            log.info("Step 3/5: Training XGBoost regressor…");
            Model<Regressor> model = modelTrainer.train(trainFeatures, trainScores);

            // Step 4
            log.info("Step 4/5: Evaluating…");
            Dataset<Regressor> testDataset = featureBuilder.buildDataset(testFeatures, testScores);
            ModelEvaluator.RegressorEvaluationResult eval = modelEvaluator.evaluateRegressor(model, testDataset);

            // Step 5
            UUID modelId = null;
            if (eval.passedQualityGate()) {
                modelId = promoteModel(model, eval, trainSize);
                log.info("Model promoted: {}", modelId);
            } else {
                log.warn("Model NOT promoted — R² {} < 0.70", String.format("%.3f", eval.r2()));
            }

            long durationMs = System.currentTimeMillis() - startTime;
            return TrainingPipelineResult.builder()
                    .success(true)
                    .trainSize(trainSize)
                    .testSize(testFeatures.size())
                    .r2(eval.r2())
                    .rmse(eval.rmse())
                    .passedQualityGate(eval.passedQualityGate())
                    .modelId(modelId)
                    .durationMs(durationMs)
                    .build();

        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - startTime;
            log.error("Rep performance pipeline failed: {}", e.getMessage(), e);
            return TrainingPipelineResult.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .durationMs(durationMs)
                    .build();
        }
    }

    // ── Synthetic data ────────────────────────────────────────────────────

    private List<SalesRepFeatures> generateRepSnapshots(int count) {
        List<SalesRepFeatures> list = new ArrayList<>();
        Random rng = new Random(42L);

        LocalDateTime baseEnd = LocalDateTime.now();

        for (int i = 0; i < count; i++) {
            int daysInPeriod = 20 + rng.nextInt(11);  // 20-30 days
            LocalDateTime periodEnd   = baseEnd.minusDays(rng.nextInt(365));
            LocalDateTime periodStart = periodEnd.minusDays(daysInPeriod);

            double visitTarget        = 50.0 + rng.nextDouble() * 100.0;
            double visitActual        = visitTarget * (0.3 + rng.nextDouble() * 0.9);
            int    ordersCreated      = (int) (visitActual * (0.2 + rng.nextDouble() * 0.6));
            double totalOrderValue    = ordersCreated * (3000.0 + rng.nextDouble() * 17000.0);
            double avgOrderValue      = ordersCreated > 0 ? totalOrderValue / ordersCreated : 0.0;
            int    activeMerchants    = 30 + rng.nextInt(70);
            int    visitedMerchants   = (int) (activeMerchants * (0.4 + rng.nextDouble() * 0.6));
            double collectionsTarget  = totalOrderValue * 0.8;
            double collectionsActual  = collectionsTarget * (0.3 + rng.nextDouble() * 0.95);

            list.add(SalesRepFeatures.builder()
                    .salesRepId(UUID.randomUUID())
                    .periodStart(periodStart)
                    .periodEnd(periodEnd)
                    .computedAt(periodEnd)
                    .visitCount((int) visitActual)
                    .visitTarget((int) visitTarget)
                    .visitCountVsTarget(visitActual / visitTarget * 100.0)
                    .ordersCreated(ordersCreated)
                    .orderConversionRate(visitActual > 0 ? ordersCreated / visitActual * 100.0 : 0.0)
                    .totalOrderValue(BigDecimal.valueOf(totalOrderValue))
                    .avgOrderValue(BigDecimal.valueOf(avgOrderValue))
                    .newMerchantsAcquired(rng.nextInt(8))
                    .activeMerchants(activeMerchants)
                    .merchantRetentionRate(visitedMerchants / (double) activeMerchants * 100.0)
                    .collectionsTarget(BigDecimal.valueOf(collectionsTarget))
                    .collectionsActual(BigDecimal.valueOf(collectionsActual))
                    .collectionRate(collectionsTarget > 0 ? collectionsActual / collectionsTarget * 100.0 : 0.0)
                    .paymentsCollected((int) (collectionsActual / 5000.0))
                    .routeVisitsPlanned((int) visitTarget)
                    .routeVisitsCompleted((int) visitActual)
                    .routeAdherencePct(visitActual / visitTarget * 100.0)
                    .assignedTerritoryMerchants(activeMerchants)
                    .visitedTerritoryMerchants(visitedMerchants)
                    .territoryPenetrationPct(visitedMerchants / (double) activeMerchants * 100.0)
                    .build());
        }
        return list;
    }

    /** Rule-based ground-truth score from feature values (0-100). */
    private List<Double> labelSnapshots(List<SalesRepFeatures> snapshots) {
        List<Double> scores = new ArrayList<>();
        for (SalesRepFeatures f : snapshots) {
            double score = 0.0;
            // Weighted components (sum = 100)
            score += Math.min(30.0, (f.visitCountVsTarget() != null ? f.visitCountVsTarget() : 0.0) * 0.30);
            score += Math.min(20.0, (f.orderConversionRate() != null ? f.orderConversionRate() : 0.0) * 0.40);
            score += Math.min(25.0, (f.collectionRate() != null ? f.collectionRate() : 0.0) * 0.25);
            score += Math.min(15.0, (f.merchantRetentionRate() != null ? f.merchantRetentionRate() : 0.0) * 0.15);
            score += Math.min(10.0, (f.routeAdherencePct() != null ? f.routeAdherencePct() : 0.0) * 0.10);
            scores.add(Math.max(0.0, Math.min(100.0, score)));
        }
        return scores;
    }

    // ── Promotion ─────────────────────────────────────────────────────────

    private UUID promoteModel(Model<Regressor> model, ModelEvaluator.RegressorEvaluationResult eval,
                               int trainSize) {
        AIModelRegistry registry = modelRegistry.registerModel(
                MODEL_NAME, "xgboost_regression",
                Map.of("algorithm", "xgboost", "num_rounds", 150),
                "training_pipeline");

        byte[] binary = serializeModel(model);
        modelRegistry.updateModelAfterTraining(registry.getId(),
                Map.of("r2", eval.r2(), "rmse", eval.rmse(), "mae", eval.mae(),
                        "training_samples", trainSize),
                binary,
                Map.of("feature_count", featureBuilder.getFeatureCount()));
        modelRegistry.promoteToActive(registry.getId());
        return registry.getId();
    }

    private byte[] serializeModel(Model<Regressor> model) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(model);
            oos.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Model serialization failed", e);
        }
    }

    @Builder
    public record TrainingPipelineResult(
            boolean success,
            String  errorMessage,
            int     trainSize,
            int     testSize,
            double  r2,
            double  rmse,
            boolean passedQualityGate,
            UUID    modelId,
            long    durationMs
    ) {}
}
