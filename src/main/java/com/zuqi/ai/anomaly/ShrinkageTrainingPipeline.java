package com.zuqi.ai.anomaly;

import com.zuqi.ai.feature.InventoryFeatures;
import com.zuqi.ai.model.ModelRegistry;
import com.zuqi.domain.ai.AIModelRegistry;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tribuo.Model;
import org.tribuo.anomaly.Event;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.math.BigDecimal;
import java.util.*;

/**
 * End-to-end training pipeline for the inventory shrinkage anomaly detector.
 *
 * Steps:
 * 1. Generate synthetic normal inventory snapshots
 * 2. Build EXPECTED-labelled dataset
 * 3. 80/20 split; inject 10% synthetic anomalies into test set
 * 4. Train LibSVM one-class model
 * 5. Evaluate: quality gate = false-positive rate < 20%
 * 6. Promote via ModelRegistry if gate passes
 *
 * Blueprint reference: plan.md Section 6.3 / implementation_plan.md Phase 4
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ShrinkageTrainingPipeline {

    private static final String MODEL_NAME   = ShrinkageDetector.MODEL_NAME;
    private static final int    NUM_PRODUCTS = 200;
    private static final int    WINDOW_DAYS  = 30;
    private static final double FPR_GATE     = 0.20;

    private final ShrinkageModelTrainer modelTrainer;
    private final AnomalyFeatureBuilder featureBuilder;
    private final ModelRegistry         modelRegistry;

    @Transactional
    public TrainingPipelineResult runPipeline() {
        log.info("{}", "=".repeat(80));
        log.info("Starting Shrinkage Detector Training Pipeline");
        log.info("{}", "=".repeat(80));

        long startTime = System.currentTimeMillis();

        try {
            // Step 1
            log.info("Step 1/6: Generating synthetic normal inventory snapshots…");
            List<InventoryFeatures> allNormal = generateNormalInventoryData(NUM_PRODUCTS, WINDOW_DAYS);
            log.info("Step 1 complete: {} normal snapshots", allNormal.size());

            // Steps 2+3: Build and split
            log.info("Step 2-3/6: Splitting 80/20 and preparing test anomalies…");
            int trainSize = (int) (allNormal.size() * 0.8);
            List<InventoryFeatures> trainNormal  = allNormal.subList(0, trainSize);
            List<InventoryFeatures> testNormal   = new ArrayList<>(allNormal.subList(trainSize, allNormal.size()));
            List<InventoryFeatures> testAnomalous = generateAnomalousInventoryData(
                    (int) (testNormal.size() * 0.1));
            log.info("Split complete: train={} test-normal={} test-anomalous={}",
                    trainNormal.size(), testNormal.size(), testAnomalous.size());

            // Step 4
            log.info("Step 4/6: Training one-class SVM…");
            Model<Event> model = modelTrainer.train(trainNormal);
            log.info("Step 4 complete");

            // Step 5
            log.info("Step 5/6: Evaluating model…");
            double fpr    = evaluateFalsePositiveRate(model, testNormal);
            double tpr    = evaluateTruePositiveRate(model, testAnomalous);
            boolean passed = fpr < FPR_GATE;
            log.info("Evaluation: FPR={} TPR={} gate={}",
                    String.format("%.3f", fpr), String.format("%.3f", tpr), passed ? "PASSED" : "FAILED");

            // Step 6
            log.info("Step 6/6: Promoting model…");
            UUID modelId = null;
            if (passed) {
                modelId = promoteModel(model, fpr, tpr, trainNormal.size());
                log.info("Model promoted to ACTIVE: {}", modelId);
            } else {
                log.warn("Model NOT promoted — FPR {} >= gate {}", String.format("%.3f", fpr), FPR_GATE);
            }

            long durationMs = System.currentTimeMillis() - startTime;
            return TrainingPipelineResult.builder()
                    .success(true)
                    .trainSize(trainNormal.size())
                    .testSize(testNormal.size() + testAnomalous.size())
                    .falsePositiveRate(fpr)
                    .truePositiveRate(tpr)
                    .passedQualityGate(passed)
                    .modelId(modelId)
                    .durationMs(durationMs)
                    .build();

        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - startTime;
            log.error("Shrinkage pipeline failed: {}", e.getMessage(), e);
            return TrainingPipelineResult.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .durationMs(durationMs)
                    .build();
        }
    }

    // ── Synthetic data ────────────────────────────────────────────────────

    private List<InventoryFeatures> generateNormalInventoryData(int numProducts, int windowDays) {
        List<InventoryFeatures> list = new ArrayList<>();
        Random rng = new Random(42L);

        for (int p = 0; p < numProducts; p++) {
            UUID warehouseId = UUID.randomUUID();
            UUID productId   = UUID.randomUUID();

            for (int d = 0; d < windowDays; d++) {
                double rate  = 10.0 + rng.nextDouble() * 90.0;
                double stock = rate * (7.0 + rng.nextDouble() * 23.0);

                list.add(InventoryFeatures.builder()
                        .warehouseId(warehouseId)
                        .productId(productId)
                        .currentStock(BigDecimal.valueOf(stock))
                        .expectedStock(BigDecimal.valueOf(stock * (0.95 + rng.nextDouble() * 0.1)))
                        .discrepancy(BigDecimal.valueOf(stock * 0.02 * (rng.nextDouble() - 0.5)))
                        .discrepancyPct(0.5 + rng.nextDouble() * 2.0)
                        .manualAdjustmentCount7d(rng.nextInt(3))
                        .adjustingUserIds(List.of(UUID.randomUUID()))
                        .consumptionRate7d(BigDecimal.valueOf(rate))
                        .consumptionRate30d(BigDecimal.valueOf(rate * (0.9 + rng.nextDouble() * 0.2)))
                        .consumptionTrend(pickTrend(rng))
                        .pendingReservedQty(BigDecimal.valueOf(rate * rng.nextDouble() * 3))
                        .expectedIncomingQty(BigDecimal.valueOf(rate * (3.0 + rng.nextDouble() * 7.0)))
                        .build());
            }
        }
        return list;
    }

    private List<InventoryFeatures> generateAnomalousInventoryData(int count) {
        List<InventoryFeatures> list = new ArrayList<>();
        Random rng = new Random(99L);

        for (int i = 0; i < count; i++) {
            double rate  = 10.0 + rng.nextDouble() * 90.0;
            double stock = rate * (7.0 + rng.nextDouble() * 23.0);

            list.add(InventoryFeatures.builder()
                    .warehouseId(UUID.randomUUID())
                    .productId(UUID.randomUUID())
                    .currentStock(BigDecimal.valueOf(stock))
                    .expectedStock(BigDecimal.valueOf(stock * 1.4))
                    .discrepancy(BigDecimal.valueOf(-stock * 0.25))
                    .discrepancyPct(-25.0 - rng.nextDouble() * 20.0)
                    .manualAdjustmentCount7d(8 + rng.nextInt(10))
                    .adjustingUserIds(List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()))
                    .consumptionRate7d(BigDecimal.valueOf(rate))
                    .consumptionRate30d(BigDecimal.valueOf(rate))
                    .consumptionTrend("DECREASING")
                    .pendingReservedQty(BigDecimal.ZERO)
                    .expectedIncomingQty(BigDecimal.ZERO)
                    .build());
        }
        return list;
    }

    private String pickTrend(Random rng) {
        return switch (rng.nextInt(3)) {
            case 0  -> "INCREASING";
            case 1  -> "DECREASING";
            default -> "STABLE";
        };
    }

    // ── Evaluation ────────────────────────────────────────────────────────

    private double evaluateFalsePositiveRate(Model<Event> model, List<InventoryFeatures> normal) {
        if (normal.isEmpty()) return 0.0;
        long fp = normal.stream()
                .map(featureBuilder::buildInventoryExample)
                .map(model::predict)
                .filter(p -> p.getOutput().getType() == Event.EventType.ANOMALOUS)
                .count();
        return (double) fp / normal.size();
    }

    private double evaluateTruePositiveRate(Model<Event> model, List<InventoryFeatures> anomalous) {
        if (anomalous.isEmpty()) return 0.0;
        long tp = anomalous.stream()
                .map(featureBuilder::buildInventoryExample)
                .map(model::predict)
                .filter(p -> p.getOutput().getType() == Event.EventType.ANOMALOUS)
                .count();
        return (double) tp / anomalous.size();
    }

    // ── Promotion ─────────────────────────────────────────────────────────

    private UUID promoteModel(Model<Event> model, double fpr, double tpr, int trainSize) {
        AIModelRegistry registry = modelRegistry.registerModel(
                MODEL_NAME, "libsvm_one_class",
                Map.of("algorithm", "one_class_svm", "kernel", "RBF"),
                "training_pipeline");

        byte[] binary = serializeModel(model);

        modelRegistry.updateModelAfterTraining(registry.getId(),
                Map.of("false_positive_rate", fpr, "true_positive_rate", tpr,
                        "training_samples", trainSize),
                binary,
                Map.of("feature_count", 8, "feature_names", List.of(
                        "discrepancy_pct", "manual_adj_count_7d", "unique_adjusting_users",
                        "consumption_rate_7d", "consumption_trend_numeric",
                        "pending_reserved_pct", "expected_incoming_pct", "current_stock_normalized")));

        modelRegistry.promoteToActive(registry.getId());
        return registry.getId();
    }

    private byte[] serializeModel(Model<Event> model) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(model);
            oos.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Model serialization failed", e);
        }
    }

    // ── Result ────────────────────────────────────────────────────────────

    @Builder
    public record TrainingPipelineResult(
            boolean success,
            String  errorMessage,
            int     trainSize,
            int     testSize,
            double  falsePositiveRate,
            double  truePositiveRate,
            boolean passedQualityGate,
            UUID    modelId,
            long    durationMs
    ) {}
}
