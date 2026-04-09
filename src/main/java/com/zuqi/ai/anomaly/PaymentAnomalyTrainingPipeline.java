package com.zuqi.ai.anomaly;

import com.zuqi.ai.feature.PaymentFeatures;
import com.zuqi.ai.model.ModelRegistry;
import com.zuqi.ai.pipeline.ModelEvaluator;
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
 * End-to-end training pipeline for the payment anomaly detector.
 *
 * Steps:
 * 1. Generate synthetic normal payment samples (500)
 * 2. Build EXPECTED-labelled dataset
 * 3. 80/20 split; inject synthetic anomalies into test set
 * 4. Train LibSVM one-class model
 * 5. Evaluate: quality gate = false-positive rate < 20%
 * 6. Promote via ModelRegistry if gate passes
 *
 * Blueprint reference: plan.md Section 6.3 / implementation_plan.md Phase 4
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentAnomalyTrainingPipeline {

    private static final String MODEL_NAME    = PaymentAnomalyDetector.MODEL_NAME;
    private static final int    NUM_PAYMENTS  = 500;

    private final PaymentAnomalyModelTrainer modelTrainer;
    private final AnomalyFeatureBuilder      featureBuilder;
    private final ModelRegistry              modelRegistry;
    private final ModelEvaluator             modelEvaluator;

    @Transactional
    public TrainingPipelineResult runPipeline() {
        log.info("{}", "=".repeat(80));
        log.info("Starting Payment Anomaly Detector Training Pipeline");
        log.info("{}", "=".repeat(80));

        long startTime = System.currentTimeMillis();

        try {
            // Steps 1-3
            log.info("Step 1/6: Generating {} synthetic normal payment samples…", NUM_PAYMENTS);
            List<PaymentFeatures> allNormal = generateNormalPayments(NUM_PAYMENTS);

            int trainSize     = (int) (allNormal.size() * 0.8);
            List<PaymentFeatures> trainNormal   = allNormal.subList(0, trainSize);
            List<PaymentFeatures> testNormal    = new ArrayList<>(allNormal.subList(trainSize, allNormal.size()));
            List<PaymentFeatures> testAnomalous = generateAnomalousPayments((int) (testNormal.size() * 0.1));

            log.info("Split: train={} test-normal={} test-anomalous={}",
                    trainNormal.size(), testNormal.size(), testAnomalous.size());

            // Step 4
            log.info("Step 4/6: Training one-class SVM…");
            Model<Event> model = modelTrainer.train(trainNormal);

            // Step 5
            log.info("Step 5/6: Evaluating model…");
            double fpr = evaluateFalsePositiveRate(model, testNormal);
            double tpr = evaluateTruePositiveRate(model, testAnomalous);
            // Gate on F1 ≥ 0.50 for the ANOMALY class (spec requirement)
            ModelEvaluator.AnomalyEvaluationResult eval = modelEvaluator.evaluateAnomalyDetector(
                    fpr, tpr, testNormal.size(), testAnomalous.size());
            boolean passed = eval.passedQualityGate();
            log.info("Evaluation: FPR={} TPR={} F1={} gate={}",
                    String.format("%.3f", fpr), String.format("%.3f", tpr),
                    String.format("%.3f", eval.f1Score()), passed ? "PASSED" : "FAILED");

            // Step 6
            UUID modelId = null;
            if (passed) {
                modelId = promoteModel(model, fpr, tpr, eval.f1Score(), trainNormal.size());
                log.info("Model promoted to ACTIVE: {}", modelId);
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
            log.error("Payment anomaly pipeline failed: {}", e.getMessage(), e);
            return TrainingPipelineResult.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .durationMs(durationMs)
                    .build();
        }
    }

    // ── Synthetic data ────────────────────────────────────────────────────

    /** Normal payments: full amounts, business hours, typical timing. */
    private List<PaymentFeatures> generateNormalPayments(int count) {
        List<PaymentFeatures> list = new ArrayList<>();
        Random rng = new Random(42L);

        for (int i = 0; i < count; i++) {
            double invoiceAmt = 5_000.0 + rng.nextDouble() * 95_000.0;
            double daysToPay  = 1.0 + rng.nextDouble() * 14.0;      // 1-14 days
            int    hour       = 8 + rng.nextInt(10);                  // 08:00-17:59

            list.add(PaymentFeatures.builder()
                    .paymentId(UUID.randomUUID())
                    .merchantId(UUID.randomUUID())
                    .daysToPay(daysToPay)
                    .daysToPayVsMerchantAvg(rng.nextDouble() * 3.0 - 1.5)  // ±1.5 days of avg
                    .gapSinceLastPaymentDays(7 + rng.nextInt(21))           // 7-28 days
                    .paymentAmount(BigDecimal.valueOf(invoiceAmt * (0.95 + rng.nextDouble() * 0.1)))
                    .invoiceAmount(BigDecimal.valueOf(invoiceAmt))
                    .amountVsInvoiceRatio(0.95 + rng.nextDouble() * 0.1)   // 95-105%
                    .amountVsMerchantAvg(0.8 + rng.nextDouble() * 0.4)     // 80-120% of avg
                    .paymentMethodEncoded(rng.nextBoolean() ? "MPESA" : "CASH")
                    .hourOfDay(hour)
                    .isPartial(false)
                    .isLate(false)
                    .merchantTotalPayments(20 + rng.nextInt(200))
                    .merchantAvgPayment(BigDecimal.valueOf(invoiceAmt))
                    .merchantAvgDaysToPay(daysToPay + rng.nextDouble() * 2.0)
                    .build());
        }
        return list;
    }

    /** Anomalous payments: late-night, zero/extreme amounts, extreme timing. */
    private List<PaymentFeatures> generateAnomalousPayments(int count) {
        List<PaymentFeatures> list = new ArrayList<>();
        Random rng = new Random(77L);

        for (int i = 0; i < count; i++) {
            double invoiceAmt = 5_000.0 + rng.nextDouble() * 95_000.0;

            list.add(PaymentFeatures.builder()
                    .paymentId(UUID.randomUUID())
                    .merchantId(UUID.randomUUID())
                    .daysToPay(rng.nextBoolean() ? 0.0 : 90.0 + rng.nextDouble() * 90.0)
                    .daysToPayVsMerchantAvg(30.0 + rng.nextDouble() * 60.0)
                    .gapSinceLastPaymentDays(rng.nextInt(2))               // very short gap
                    .paymentAmount(BigDecimal.valueOf(invoiceAmt * 0.01))  // near-zero
                    .invoiceAmount(BigDecimal.valueOf(invoiceAmt))
                    .amountVsInvoiceRatio(0.01)
                    .amountVsMerchantAvg(0.05)
                    .paymentMethodEncoded("CASH")
                    .hourOfDay(1 + rng.nextInt(5))                         // 01:00-05:59
                    .isPartial(true)
                    .isLate(true)
                    .merchantTotalPayments(1)
                    .merchantAvgPayment(BigDecimal.valueOf(invoiceAmt))
                    .merchantAvgDaysToPay(7.0)
                    .build());
        }
        return list;
    }

    // ── Evaluation ────────────────────────────────────────────────────────

    private double evaluateFalsePositiveRate(Model<Event> model, List<PaymentFeatures> normal) {
        if (normal.isEmpty()) return 0.0;
        long fp = normal.stream()
                .map(featureBuilder::buildPaymentExample)
                .map(model::predict)
                .filter(p -> p.getOutput().getType() == Event.EventType.ANOMALOUS)
                .count();
        return (double) fp / normal.size();
    }

    private double evaluateTruePositiveRate(Model<Event> model, List<PaymentFeatures> anomalous) {
        if (anomalous.isEmpty()) return 0.0;
        long tp = anomalous.stream()
                .map(featureBuilder::buildPaymentExample)
                .map(model::predict)
                .filter(p -> p.getOutput().getType() == Event.EventType.ANOMALOUS)
                .count();
        return (double) tp / anomalous.size();
    }

    // ── Promotion ─────────────────────────────────────────────────────────

    private UUID promoteModel(Model<Event> model, double fpr, double tpr, double f1, int trainSize) {
        AIModelRegistry registry = modelRegistry.registerModel(
                MODEL_NAME, "libsvm_one_class",
                Map.of("algorithm", "one_class_svm", "kernel", "RBF"),
                "training_pipeline");

        byte[] binary = serializeModel(model);
        modelRegistry.updateModelAfterTraining(registry.getId(),
                Map.of("false_positive_rate", fpr, "true_positive_rate", tpr, "f1_score", f1,
                        "training_samples", trainSize),
                binary,
                Map.of("feature_count", 10));
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
