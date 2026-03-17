package com.zuqi.ai.anomaly;

import com.zuqi.ai.event.OrderCreatedEvent;
import com.zuqi.ai.model.ModelRegistry;
import com.zuqi.domain.ai.AIModelRegistry;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tribuo.Model;
import org.tribuo.classification.Label;
import org.tribuo.classification.evaluation.LabelEvaluation;
import org.tribuo.classification.evaluation.LabelEvaluator;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * End-to-end training pipeline for the Tier-2 data quality XGBoost classifier.
 *
 * Steps:
 * 1. Generate synthetic NORMAL and ANOMALOUS order events
 * 2. 80/20 train/test split
 * 3. Train XGBoost classifier (NORMAL / ANOMALOUS)
 * 4. Evaluate: quality gate = F1 (ANOMALOUS class) >= 0.65
 * 5. Promote via ModelRegistry if gate passes
 *
 * Synthetic anomaly injection covers the patterns most likely to
 * slip past the Tier-1 rules engine:
 * - Order value 5-20× above merchant's historical average (inflated pricing)
 * - Bulk quantity spike (single item = 80%+ of total order value)
 * - Unusual ordering hour (2-4 AM) combined with abnormal value
 * - Price-per-unit 3× above the product catalog price
 *
 * Blueprint reference: plan.md Section 6.3 - DataQualityDetector Tier-2
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DataQualityTrainingPipeline {

    static final String MODEL_NAME      = "data_quality_classifier";
    static final int    NUM_MERCHANTS   = 400;
    static final int    ORDERS_PER_MERCHANT = 5;
    static final double ANOMALY_RATE    = 0.05;  // 5% of orders are anomalous
    static final double F1_GATE         = 0.65;  // minimum ANOMALOUS-class F1

    private final DataQualityModelTrainer  modelTrainer;
    private final DataQualityFeatureBuilder featureBuilder;
    private final ModelRegistry            modelRegistry;

    @Transactional
    public TrainingPipelineResult runPipeline() {
        log.info("{}", "=".repeat(80));
        log.info("Starting Data Quality Classifier Training Pipeline");
        log.info("{}", "=".repeat(80));

        long startTime = System.currentTimeMillis();

        try {
            // Step 1: Generate synthetic data
            log.info("Step 1/5: Generating synthetic order events…");
            List<OrderCreatedEvent> allNormal    = generateNormalOrders(NUM_MERCHANTS, ORDERS_PER_MERCHANT);
            int anomalyCount = Math.max(DataQualityModelTrainer.MINIMUM_ANOMALOUS_COUNT,
                    (int)(allNormal.size() * ANOMALY_RATE));
            List<OrderCreatedEvent> allAnomalous = generateAnomalousOrders(anomalyCount);
            log.info("Step 1 complete: {} normal, {} anomalous", allNormal.size(), allAnomalous.size());

            // Step 2: Split 80/20
            log.info("Step 2/5: Splitting train/test sets…");
            int trainNormalSize = (int)(allNormal.size() * 0.8);
            List<OrderCreatedEvent> trainNormal    = allNormal.subList(0, trainNormalSize);
            List<OrderCreatedEvent> testNormal     = allNormal.subList(trainNormalSize, allNormal.size());
            int trainAnomalousSize = (int)(allAnomalous.size() * 0.8);
            List<OrderCreatedEvent> trainAnomalous = allAnomalous.subList(0, trainAnomalousSize);
            List<OrderCreatedEvent> testAnomalous  = allAnomalous.subList(trainAnomalousSize, allAnomalous.size());
            log.info("Split: train={} normal + {} anomalous; test={} normal + {} anomalous",
                    trainNormal.size(), trainAnomalous.size(), testNormal.size(), testAnomalous.size());

            // Step 3: Train
            log.info("Step 3/5: Training XGBoost classifier…");
            Model<Label> model = modelTrainer.train(trainNormal, trainAnomalous);
            log.info("Step 3 complete");

            // Step 4: Evaluate
            log.info("Step 4/5: Evaluating…");
            EvaluationResult eval = evaluate(model, testNormal, testAnomalous);
            boolean passed = eval.f1Anomalous() >= F1_GATE;
            log.info("Evaluation: precision={} recall={} f1={} gate={}",
                    String.format("%.3f", eval.precisionAnomalous()),
                    String.format("%.3f", eval.recallAnomalous()),
                    String.format("%.3f", eval.f1Anomalous()),
                    passed ? "PASSED" : "FAILED");

            // Step 5: Promote
            log.info("Step 5/5: Promoting model…");
            UUID modelId = null;
            if (passed) {
                modelId = promoteModel(model, eval, trainNormal.size() + trainAnomalous.size());
                log.info("Data quality model promoted to ACTIVE: {}", modelId);
            } else {
                log.warn("Model NOT promoted — F1 {} < gate {}", String.format("%.3f", eval.f1Anomalous()), F1_GATE);
            }

            long durationMs = System.currentTimeMillis() - startTime;
            return TrainingPipelineResult.builder()
                    .success(true)
                    .trainSize(trainNormal.size() + trainAnomalous.size())
                    .testSize(testNormal.size() + testAnomalous.size())
                    .precisionAnomalous(eval.precisionAnomalous())
                    .recallAnomalous(eval.recallAnomalous())
                    .f1Anomalous(eval.f1Anomalous())
                    .passedQualityGate(passed)
                    .modelId(modelId)
                    .durationMs(durationMs)
                    .build();

        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - startTime;
            log.error("Data quality pipeline failed: {}", e.getMessage(), e);
            return TrainingPipelineResult.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .durationMs(durationMs)
                    .build();
        }
    }

    // ── Synthetic data ────────────────────────────────────────────────────

    List<OrderCreatedEvent> generateNormalOrders(int numMerchants, int ordersPerMerchant) {
        List<OrderCreatedEvent> orders = new ArrayList<>();
        Random rng = new Random(42L);

        for (int m = 0; m < numMerchants; m++) {
            UUID merchantId    = UUID.randomUUID();
            UUID distributorId = UUID.randomUUID();
            UUID salesRepId    = UUID.randomUUID();

            // Each merchant has a typical order value range
            double avgUnitPrice = 500 + rng.nextDouble() * 4_500;
            int avgItemCount = 2 + rng.nextInt(8);

            for (int o = 0; o < ordersPerMerchant; o++) {
                int itemCount = Math.max(1, avgItemCount + rng.nextInt(3) - 1);
                List<OrderCreatedEvent.OrderItem> items = new ArrayList<>();
                BigDecimal total = BigDecimal.ZERO;

                for (int i = 0; i < itemCount; i++) {
                    int qty = 1 + rng.nextInt(20);
                    // Vary price ±20% around this merchant's typical unit price
                    BigDecimal price = BigDecimal.valueOf(avgUnitPrice * (0.8 + rng.nextDouble() * 0.4));
                    items.add(new OrderCreatedEvent.OrderItem(
                            UUID.randomUUID(), qty, price, price.multiply(BigDecimal.valueOf(qty))));
                    total = total.add(price.multiply(BigDecimal.valueOf(qty)));
                }

                // Normal hour: 7 AM - 8 PM
                int hour = 7 + rng.nextInt(13);
                LocalDateTime createdAt = LocalDateTime.now()
                        .minusDays(ordersPerMerchant - o)
                        .withHour(hour);

                orders.add(new OrderCreatedEvent(
                        UUID.randomUUID(), merchantId, salesRepId, distributorId,
                        total, items, createdAt, "REGULAR"));
            }
        }
        return orders;
    }

    List<OrderCreatedEvent> generateAnomalousOrders(int count) {
        List<OrderCreatedEvent> orders = new ArrayList<>();
        Random rng = new Random(99L);

        for (int i = 0; i < count; i++) {
            int anomalyType = rng.nextInt(4);
            UUID merchantId    = UUID.randomUUID();
            UUID distributorId = UUID.randomUUID();

            orders.add(switch (anomalyType) {
                case 0 -> buildInflatedValueOrder(merchantId, distributorId, rng);
                case 1 -> buildBulkQtySpikeOrder(merchantId, distributorId, rng);
                case 2 -> buildOddHourOrder(merchantId, distributorId, rng);
                default -> buildCatalogPriceViolationOrder(merchantId, distributorId, rng);
            });
        }
        return orders;
    }

    /** 5-20× above typical merchant value — inflated pricing fraud */
    private OrderCreatedEvent buildInflatedValueOrder(UUID merchantId, UUID distId, Random rng) {
        BigDecimal price = BigDecimal.valueOf(50_000 + rng.nextDouble() * 450_000);
        int qty = 1 + rng.nextInt(5);
        BigDecimal total = price.multiply(BigDecimal.valueOf(qty));
        List<OrderCreatedEvent.OrderItem> items = List.of(
                new OrderCreatedEvent.OrderItem(UUID.randomUUID(), qty, price, total));
        return new OrderCreatedEvent(UUID.randomUUID(), merchantId, UUID.randomUUID(), distId,
                total, items, LocalDateTime.now(), "REGULAR");
    }

    /** Single item = 95%+ of order — suspicious concentration */
    private OrderCreatedEvent buildBulkQtySpikeOrder(UUID merchantId, UUID distId, Random rng) {
        int qty = 5_000 + rng.nextInt(5_000);
        BigDecimal price = BigDecimal.valueOf(100 + rng.nextDouble() * 200);
        BigDecimal total = price.multiply(BigDecimal.valueOf(qty));
        List<OrderCreatedEvent.OrderItem> items = List.of(
                new OrderCreatedEvent.OrderItem(UUID.randomUUID(), qty, price, total));
        return new OrderCreatedEvent(UUID.randomUUID(), merchantId, UUID.randomUUID(), distId,
                total, items, LocalDateTime.now(), "REGULAR");
    }

    /** Placed at 2-4 AM with abnormal value */
    private OrderCreatedEvent buildOddHourOrder(UUID merchantId, UUID distId, Random rng) {
        int qty = 10 + rng.nextInt(50);
        BigDecimal price = BigDecimal.valueOf(20_000 + rng.nextDouble() * 80_000);
        BigDecimal total = price.multiply(BigDecimal.valueOf(qty));
        List<OrderCreatedEvent.OrderItem> items = List.of(
                new OrderCreatedEvent.OrderItem(UUID.randomUUID(), qty, price, total));
        LocalDateTime oddHour = LocalDateTime.now().withHour(2 + rng.nextInt(3));
        return new OrderCreatedEvent(UUID.randomUUID(), merchantId, UUID.randomUUID(), distId,
                total, items, oddHour, "REGULAR");
    }

    /** Unit price 5-10× above catalog (catalog not checked in training — high absolute price signals it) */
    private OrderCreatedEvent buildCatalogPriceViolationOrder(UUID merchantId, UUID distId, Random rng) {
        int qty = 1 + rng.nextInt(10);
        BigDecimal price = BigDecimal.valueOf(100_000 + rng.nextDouble() * 900_000);
        BigDecimal total = price.multiply(BigDecimal.valueOf(qty));
        List<OrderCreatedEvent.OrderItem> items = List.of(
                new OrderCreatedEvent.OrderItem(UUID.randomUUID(), qty, price, total));
        return new OrderCreatedEvent(UUID.randomUUID(), merchantId, UUID.randomUUID(), distId,
                total, items, LocalDateTime.now(), "REGULAR");
    }

    // ── Evaluation ────────────────────────────────────────────────────────

    private EvaluationResult evaluate(Model<Label> model,
                                       List<OrderCreatedEvent> testNormal,
                                       List<OrderCreatedEvent> testAnomalous) {
        // Build test dataset
        var dataset = featureBuilder.buildTrainingDataset(testNormal, testAnomalous);
        LabelEvaluator evaluator = new LabelEvaluator();
        LabelEvaluation eval = evaluator.evaluate(model, dataset);

        Label anomalousLabel = DataQualityFeatureBuilder.ANOMALOUS;
        double precision = eval.precision(anomalousLabel);
        double recall    = eval.recall(anomalousLabel);
        double f1        = (precision + recall > 0)
                ? 2 * precision * recall / (precision + recall) : 0.0;

        return new EvaluationResult(precision, recall, f1);
    }

    private record EvaluationResult(double precisionAnomalous, double recallAnomalous, double f1Anomalous) {}

    // ── Promotion ─────────────────────────────────────────────────────────

    private UUID promoteModel(Model<Label> model, EvaluationResult eval, int trainSize) {
        AIModelRegistry registry = modelRegistry.registerModel(
                MODEL_NAME, "xgboost_classifier",
                Map.of("algorithm", "xgboost", "type", "binary_classification"),
                "training_pipeline");

        byte[] binary = serializeModel(model);

        modelRegistry.updateModelAfterTraining(registry.getId(),
                Map.of("precision_anomalous", eval.precisionAnomalous(),
                        "recall_anomalous",    eval.recallAnomalous(),
                        "f1_anomalous",        eval.f1Anomalous(),
                        "training_samples",    trainSize),
                binary,
                Map.of("feature_count", 14, "feature_names", List.of(
                        "item_count", "total_amount_log1p", "max_item_qty",
                        "has_zero_price_item", "max_item_value_pct", "price_coefficient_of_variation",
                        "order_type_encoded", "hour_of_day",
                        "order_value_vs_merchant_avg", "order_value_z_score",
                        "days_since_last_order", "order_frequency_ratio",
                        "item_count_vs_merchant_avg", "price_consistency_score")));

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

    // ── Result record ─────────────────────────────────────────────────────

    @Builder
    public record TrainingPipelineResult(
            boolean success,
            String  errorMessage,
            int     trainSize,
            int     testSize,
            double  precisionAnomalous,
            double  recallAnomalous,
            double  f1Anomalous,
            boolean passedQualityGate,
            UUID    modelId,
            long    durationMs
    ) {}
}
