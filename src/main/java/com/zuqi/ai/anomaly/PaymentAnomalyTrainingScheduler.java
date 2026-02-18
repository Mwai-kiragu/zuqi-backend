package com.zuqi.ai.anomaly;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Scheduled weekly retraining of the payment anomaly detector.
 *
 * Blueprint reference: implementation_plan.md Phase 4
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
        prefix = "zuqi.ai.anomaly-detection",
        name = "training-enabled",
        havingValue = "true",
        matchIfMissing = false
)
public class PaymentAnomalyTrainingScheduler {

    private final PaymentAnomalyTrainingPipeline trainingPipeline;
    private final MeterRegistry                  meterRegistry;

    @Scheduled(cron = "${zuqi.ai.anomaly-detection.payment-training-cron:0 30 2 ? * SUN}")
    public void weeklyTraining() {
        log.info("{}", "=".repeat(80));
        log.info("WEEKLY PAYMENT ANOMALY MODEL TRAINING - STARTED");
        log.info("{}", "=".repeat(80));

        try {
            PaymentAnomalyTrainingPipeline.TrainingPipelineResult result = trainingPipeline.runPipeline();

            if (!result.success()) {
                log.error("Payment anomaly training FAILED: {}", result.errorMessage());
                recordFailureMetric();
                return;
            }

            log.info("Payment anomaly training SUCCEEDED: FPR={} TPR={} modelId={}",
                    String.format("%.3f", result.falsePositiveRate()),
                    String.format("%.3f", result.truePositiveRate()),
                    result.modelId());

            recordSuccessMetrics(result);

        } catch (Exception e) {
            log.error("Payment anomaly training failed with exception: {}", e.getMessage(), e);
            recordFailureMetric();
        }
    }

    private void recordSuccessMetrics(PaymentAnomalyTrainingPipeline.TrainingPipelineResult result) {
        Counter.builder("zuqi_ai_payment_anomaly_training_success")
                .description("Successful payment anomaly model training runs")
                .register(meterRegistry)
                .increment();
        meterRegistry.gauge("zuqi_ai_payment_anomaly_training_fpr", result.falsePositiveRate());
        meterRegistry.gauge("zuqi_ai_payment_anomaly_training_tpr", result.truePositiveRate());
    }

    private void recordFailureMetric() {
        Counter.builder("zuqi_ai_payment_anomaly_training_failure")
                .description("Failed payment anomaly model training runs")
                .register(meterRegistry)
                .increment();
    }
}
