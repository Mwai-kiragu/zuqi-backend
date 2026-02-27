package com.zuqi.ai.anomaly;

import com.zuqi.ai.feature.MerchantPaymentTrendFeatures;
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
import java.util.*;

/**
 * End-to-end training pipeline for payment distress classification.
 *
 * <p>Steps:
 * <ol>
 *   <li>Generate 600 synthetic merchant payment-trend snapshots</li>
 *   <li>Label: DISTRESS if latePaymentRate3m &gt; 0.5 OR consecutiveMissedOrders &gt; 2</li>
 *   <li>80/20 train/test split</li>
 *   <li>Train XGBoost classifier</li>
 *   <li>Evaluate: quality gate = AUC &ge; 0.75</li>
 *   <li>Promote via ModelRegistry</li>
 * </ol>
 *
 * <p>Blueprint reference: implementation_plan.md Phase 6 — Payment Distress Classification
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentDistressTrainingPipeline {

    private static final String MODEL_NAME     = "payment_distress_classifier";
    private static final int    NUM_MERCHANTS  = 600;

    private final PaymentDistressModelTrainer modelTrainer;
    private final PaymentDistressFeatureBuilder featureBuilder;
    private final ModelEvaluator              modelEvaluator;
    private final ModelRegistry              modelRegistry;

    @Transactional
    public TrainingPipelineResult runPipeline() {
        log.info("{}", "=".repeat(80));
        log.info("Starting Payment Distress Classifier Training Pipeline");
        log.info("{}", "=".repeat(80));

        long startTime = System.currentTimeMillis();

        try {
            // Step 1+2: Generate and label
            log.info("Step 1/6: Generating {} synthetic merchant trend snapshots…", NUM_MERCHANTS);
            List<MerchantPaymentTrendFeatures> snapshots = generateMerchantTrendSnapshots(NUM_MERCHANTS);
            List<String> labels = labelSnapshots(snapshots);

            long distressed = labels.stream()
                    .filter(PaymentDistressFeatureBuilder.LABEL_DISTRESS::equals).count();
            log.info("Labels: {} DISTRESS, {} NO_DISTRESS", distressed, labels.size() - distressed);

            // Step 3: Split 80/20
            int trainSize = (int) (snapshots.size() * 0.8);
            List<MerchantPaymentTrendFeatures> trainFeatures = snapshots.subList(0, trainSize);
            List<String>                       trainLabels   = labels.subList(0, trainSize);
            List<MerchantPaymentTrendFeatures> testFeatures  = snapshots.subList(trainSize, snapshots.size());
            List<String>                       testLabels    = labels.subList(trainSize, labels.size());

            // Step 4: Train
            log.info("Step 4/6: Training XGBoost classifier…");
            Model<Label> model = modelTrainer.train(trainFeatures, trainLabels);

            // Step 5: Evaluate
            log.info("Step 5/6: Evaluating…");
            Dataset<Label> testDataset = featureBuilder.buildDataset(testFeatures, testLabels);
            ModelEvaluator.ClassifierEvaluationResult eval = modelEvaluator.evaluateClassifier(model, testDataset);

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
            log.error("Payment distress pipeline failed: {}", e.getMessage(), e);
            return TrainingPipelineResult.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .durationMs(durationMs)
                    .build();
        }
    }

    // ── Synthetic data ────────────────────────────────────────────────────

    /**
     * Generate synthetic merchant payment-trend snapshots with realistic distributions.
     *
     * <p>Approximately 25% of snapshots exhibit distress signals (high late-payment
     * rates, missed orders, rising utilisation) to ensure balanced training.
     */
    private List<MerchantPaymentTrendFeatures> generateMerchantTrendSnapshots(int count) {
        List<MerchantPaymentTrendFeatures> list = new ArrayList<>();
        Random rng = new Random(42L);

        for (int i = 0; i < count; i++) {
            boolean makeDistressed = rng.nextDouble() < 0.25;

            if (makeDistressed) {
                list.add(generateDistressedMerchant(rng));
            } else {
                list.add(generateHealthyMerchant(rng));
            }
        }

        // Shuffle to avoid ordering bias
        Collections.shuffle(list, new Random(99L));
        return list;
    }

    private MerchantPaymentTrendFeatures generateHealthyMerchant(Random rng) {
        return MerchantPaymentTrendFeatures.builder()
                .merchantId(UUID.randomUUID())
                // Payment timing — generally on time
                .daysToPayTrend3m(-0.5 + rng.nextDouble() * 1.0)       // -0.5 to 0.5
                .daysToPayStddev3m(1.0 + rng.nextDouble() * 3.0)       // 1-4 days
                .latePaymentRate3m(rng.nextDouble() * 0.20)             // 0-20%
                .latePaymentRateTrend3m(-0.1 + rng.nextDouble() * 0.15) // stable/improving
                // Order frequency — regular
                .orderFrequency3m(1.0 + rng.nextDouble() * 4.0)        // 1-5 per week
                .orderFrequencyTrend3m(-0.05 + rng.nextDouble() * 0.15) // stable/growing
                .consecutiveMissedOrders(rng.nextInt(2))                // 0-1
                // Credit utilisation — healthy
                .creditUtilization3m(0.1 + rng.nextDouble() * 0.5)     // 10-60%
                .creditUtilizationTrajectory(-0.05 + rng.nextDouble() * 0.1) // stable
                .peakUtilization3m(0.3 + rng.nextDouble() * 0.4)       // 30-70%
                .hitCreditLimit3m(false)
                // Partial payments — rare
                .partialPaymentFreq3m(rng.nextDouble() * 0.10)         // 0-10%
                .partialPaymentFreqTrend3m(-0.05 + rng.nextDouble() * 0.05)
                .consecutivePartialPayments(0)
                // Order values — stable
                .avgOrderValue3m(5_000.0 + rng.nextDouble() * 50_000.0)
                .avgOrderValueTrend3m(-0.05 + rng.nextDouble() * 0.15) // stable/growing
                .orderValueVolatility3m(500.0 + rng.nextDouble() * 5_000.0)
                // Financial health — good
                .totalOutstanding(BigDecimal.valueOf(rng.nextDouble() * 100_000.0))
                .outstandingTrend3m(-0.1 + rng.nextDouble() * 0.1)     // stable/decreasing
                .daysOverdueMax(rng.nextInt(15))                        // 0-14 days
                .paymentToOrderRatio3m(0.85 + rng.nextDouble() * 0.20) // 85-105%
                .build();
    }

    private MerchantPaymentTrendFeatures generateDistressedMerchant(Random rng) {
        return MerchantPaymentTrendFeatures.builder()
                .merchantId(UUID.randomUUID())
                // Payment timing — deteriorating
                .daysToPayTrend3m(2.0 + rng.nextDouble() * 5.0)        // increasing rapidly
                .daysToPayStddev3m(5.0 + rng.nextDouble() * 10.0)      // high volatility
                .latePaymentRate3m(0.50 + rng.nextDouble() * 0.50)     // 50-100%
                .latePaymentRateTrend3m(0.10 + rng.nextDouble() * 0.30) // worsening
                // Order frequency — declining
                .orderFrequency3m(rng.nextDouble() * 2.0)              // 0-2 per week
                .orderFrequencyTrend3m(-0.30 - rng.nextDouble() * 0.30) // declining
                .consecutiveMissedOrders(2 + rng.nextInt(5))            // 2-6 missed
                // Credit utilisation — maxed out
                .creditUtilization3m(0.70 + rng.nextDouble() * 0.30)   // 70-100%
                .creditUtilizationTrajectory(0.05 + rng.nextDouble() * 0.15) // rising
                .peakUtilization3m(0.85 + rng.nextDouble() * 0.15)     // 85-100%
                .hitCreditLimit3m(rng.nextDouble() < 0.7)              // 70% hit limit
                // Partial payments — frequent
                .partialPaymentFreq3m(0.30 + rng.nextDouble() * 0.50)  // 30-80%
                .partialPaymentFreqTrend3m(0.05 + rng.nextDouble() * 0.20) // increasing
                .consecutivePartialPayments(1 + rng.nextInt(5))        // 1-5 streak
                // Order values — declining
                .avgOrderValue3m(2_000.0 + rng.nextDouble() * 20_000.0) // lower value
                .avgOrderValueTrend3m(-0.20 - rng.nextDouble() * 0.20) // declining
                .orderValueVolatility3m(5_000.0 + rng.nextDouble() * 15_000.0) // high volatility
                // Financial health — poor
                .totalOutstanding(BigDecimal.valueOf(100_000.0 + rng.nextDouble() * 400_000.0))
                .outstandingTrend3m(0.10 + rng.nextDouble() * 0.30)    // rising
                .daysOverdueMax(30 + rng.nextInt(90))                   // 30-120 days
                .paymentToOrderRatio3m(0.30 + rng.nextDouble() * 0.40) // 30-70%
                .build();
    }

    /**
     * Label: DISTRESS if latePaymentRate3m &gt; 0.5 OR consecutiveMissedOrders &gt; 2.
     */
    private List<String> labelSnapshots(List<MerchantPaymentTrendFeatures> snapshots) {
        List<String> labels = new ArrayList<>();
        for (MerchantPaymentTrendFeatures f : snapshots) {
            double lateRate = f.latePaymentRate3m() != null ? f.latePaymentRate3m() : 0.0;
            int missedOrders = f.consecutiveMissedOrders() != null ? f.consecutiveMissedOrders() : 0;

            boolean isDistressed = lateRate > 0.5 || missedOrders > 2;
            labels.add(isDistressed
                    ? PaymentDistressFeatureBuilder.LABEL_DISTRESS
                    : PaymentDistressFeatureBuilder.LABEL_NO_DISTRESS);
        }
        return labels;
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
