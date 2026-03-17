package com.zuqi.ai.prediction;

import com.zuqi.ai.feature.InventoryFeatures;
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
import org.tribuo.classification.Label;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * End-to-end training pipeline for stockout prediction classifier.
 *
 * Steps:
 * 1. Generate 500 synthetic inventory snapshots with varied stock levels
 * 2. Label: STOCKOUT if daysOfStockRemaining < 3 and expectedIncoming < consumptionRate7d × 5
 * 3. 80/20 train/test split
 * 4. Train XGBoost classifier
 * 5. Evaluate: quality gate = AUC ≥ 0.75
 * 6. Promote via ModelRegistry
 *
 * Blueprint reference: implementation_plan.md Phase 4, Step 7a
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StockoutTrainingPipeline {

    private static final String MODEL_NAME    = StockoutPredictor.MODEL_NAME;
    private static final int    NUM_SNAPSHOTS = 500;

    private final StockoutModelTrainer  modelTrainer;
    private final StockoutFeatureBuilder featureBuilder;
    private final ModelEvaluator        modelEvaluator;
    private final ModelRegistry         modelRegistry;

    @Transactional
    public TrainingPipelineResult runPipeline() {
        log.info("{}", "=".repeat(80));
        log.info("Starting Stockout Predictor Training Pipeline");
        log.info("{}", "=".repeat(80));

        long startTime = System.currentTimeMillis();

        try {
            // Step 1+2: Generate and label
            log.info("Step 1/6: Generating {} synthetic inventory snapshots…", NUM_SNAPSHOTS);
            List<InventoryFeatures> snapshots = generateInventorySnapshots(NUM_SNAPSHOTS);
            List<String> labels = labelSnapshots(snapshots);

            long stockouts = labels.stream().filter(StockoutFeatureBuilder.LABEL_STOCKOUT::equals).count();
            log.info("Labels: {} STOCKOUT, {} NO_STOCKOUT", stockouts, labels.size() - stockouts);

            // Step 3: Split
            int trainSize = (int) (snapshots.size() * 0.8);
            List<InventoryFeatures> trainFeatures = snapshots.subList(0, trainSize);
            List<String>            trainLabels   = labels.subList(0, trainSize);
            List<InventoryFeatures> testFeatures  = snapshots.subList(trainSize, snapshots.size());
            List<String>            testLabels    = labels.subList(trainSize, labels.size());

            // Step 4: Train
            log.info("Step 4/6: Training XGBoost classifier…");
            Model<Label> model = modelTrainer.train(trainFeatures, trainLabels);

            // Step 5: Evaluate
            log.info("Step 5/6: Evaluating…");
            Dataset<Label> testDataset = featureBuilder.buildDataset(testFeatures, testLabels);
            ModelEvaluator.ClassifierEvaluationResult eval = modelEvaluator.evaluateClassifier(
                    model, testDataset, StockoutFeatureBuilder.LABEL_STOCKOUT);

            // Step 6: Promote
            UUID modelId = null;
            if (eval.passedQualityGate()) {
                modelId = promoteModel(model, eval, trainSize);
                log.info("Model promoted: {}", modelId);
            } else {
                log.warn("Model NOT promoted — AUC {} < 0.75", String.format("%.3f", eval.aucRoc()));
            }

            long durationMs = System.currentTimeMillis() - startTime;
            return TrainingPipelineResult.builder()
                    .success(true)
                    .trainSize(trainSize)
                    .testSize(testFeatures.size())
                    .aucRoc(eval.aucRoc())
                    .accuracy(eval.accuracy())
                    .passedQualityGate(eval.passedQualityGate())
                    .modelId(modelId)
                    .durationMs(durationMs)
                    .build();

        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - startTime;
            log.error("Stockout pipeline failed: {}", e.getMessage(), e);
            return TrainingPipelineResult.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .durationMs(durationMs)
                    .build();
        }
    }

    // ── Synthetic data ────────────────────────────────────────────────────

    private List<InventoryFeatures> generateInventorySnapshots(int count) {
        List<InventoryFeatures> list = new ArrayList<>();
        Random rng = new Random(42L);

        // 30% stockout scenarios, 70% healthy — ensures the model sees enough of each class
        int stockoutCount = (int) (count * 0.30);

        for (int i = 0; i < count; i++) {
            boolean isStockoutScenario = i < stockoutCount;
            double dailyRate = 5.0 + rng.nextDouble() * 95.0;
            int    month     = 1 + rng.nextInt(12);
            double forecastNoise = 0.85 + rng.nextDouble() * 0.30;
            BigDecimal predictedDemand7d = BigDecimal.valueOf(dailyRate * 7 * forecastNoise);

            double stock, incoming;
            if (isStockoutScenario) {
                // Engineer stockout: < 3 days stock, very little incoming
                stock    = dailyRate * rng.nextDouble() * 2.9;           // 0–2.9 days of stock
                incoming = dailyRate * rng.nextDouble() * 0.5;            // < half a day incoming
            } else {
                // Engineer healthy: 3–30 days stock, normal incoming
                stock    = dailyRate * (3.0 + rng.nextDouble() * 27.0);  // 3–30 days
                incoming = dailyRate * rng.nextDouble() * 14.0;           // 0–14 days incoming
            }

            list.add(InventoryFeatures.builder()
                    .warehouseId(UUID.randomUUID())
                    .productId(UUID.randomUUID())
                    .computedAt(LocalDateTime.now().withMonth(month))
                    .currentStock(BigDecimal.valueOf(Math.max(0, stock)))
                    .consumptionRate7d(BigDecimal.valueOf(dailyRate * 7))
                    .consumptionRate30d(BigDecimal.valueOf(dailyRate * 30))
                    .consumptionTrend(pickTrend(rng))
                    .pendingReservedQty(BigDecimal.valueOf(dailyRate * rng.nextDouble() * 3))
                    .expectedIncomingQty(BigDecimal.valueOf(incoming))
                    .discrepancyPct(rng.nextDouble() * 5.0 - 2.5)
                    .manualAdjustmentCount7d(rng.nextInt(5))
                    .adjustingUserIds(List.of(UUID.randomUUID()))
                    .discrepancy(BigDecimal.valueOf(stock * 0.01))
                    .expectedStock(BigDecimal.valueOf(stock * 1.01))
                    .predictedDemand7d(predictedDemand7d)
                    .build());
        }
        // Shuffle so stockout/healthy examples are interleaved (not all stockouts first)
        Collections.shuffle(list, rng);
        return list;
    }

    /** Label STOCKOUT if daysRemaining < 3 and expectedIncoming < consumptionRate7d × 5 days. */
    private List<String> labelSnapshots(List<InventoryFeatures> snapshots) {
        List<String> labels = new ArrayList<>();
        for (InventoryFeatures f : snapshots) {
            double days    = featureBuilder.computeDaysOfStockRemaining(f);
            double incoming = f.expectedIncomingQty() != null ? f.expectedIncomingQty().doubleValue() : 0.0;
            double rate7d   = f.consumptionRate7d() != null ? f.consumptionRate7d().doubleValue() : 1.0;
            boolean isStockout = days < 3.0 && incoming < (rate7d / 7.0) * 5.0;
            labels.add(isStockout ? StockoutFeatureBuilder.LABEL_STOCKOUT : StockoutFeatureBuilder.LABEL_NO_STOCKOUT);
        }
        return labels;
    }

    private String pickTrend(Random rng) {
        return switch (rng.nextInt(3)) {
            case 0  -> "INCREASING";
            case 1  -> "DECREASING";
            default -> "STABLE";
        };
    }

    // ── Promotion ─────────────────────────────────────────────────────────

    private UUID promoteModel(Model<Label> model, ModelEvaluator.ClassifierEvaluationResult eval,
                               int trainSize) {
        AIModelRegistry registry = modelRegistry.registerModel(
                MODEL_NAME, "xgboost_classification",
                Map.of("algorithm", "xgboost", "num_rounds", 100),
                "training_pipeline");

        byte[] binary = serializeModel(model);
        modelRegistry.updateModelAfterTraining(registry.getId(),
                Map.of("auc_roc", eval.aucRoc(), "accuracy", eval.accuracy(),
                        "training_samples", trainSize),
                binary,
                Map.of("feature_count", featureBuilder.getFeatureCount()));
        modelRegistry.promoteToActive(registry.getId());
        return registry.getId();
    }

    private byte[] serializeModel(Model<Label> model) {
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
            double  aucRoc,
            double  accuracy,
            boolean passedQualityGate,
            UUID    modelId,
            long    durationMs
    ) {}
}
