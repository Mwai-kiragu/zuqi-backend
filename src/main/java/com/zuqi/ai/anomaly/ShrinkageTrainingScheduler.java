package com.zuqi.ai.anomaly;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Scheduled weekly retraining of the inventory shrinkage anomaly detector.
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
public class ShrinkageTrainingScheduler {

    private final ShrinkageTrainingPipeline trainingPipeline;
    private final MeterRegistry             meterRegistry;

    @Scheduled(cron = "${zuqi.ai.anomaly-detection.shrinkage-training-cron:0 0 2 ? * SUN}")
    public void weeklyTraining() {
        log.info("{}", "=".repeat(80));
        log.info("WEEKLY SHRINKAGE MODEL TRAINING - STARTED");
        log.info("{}", "=".repeat(80));

        long startTime = System.currentTimeMillis();

        try {
            ShrinkageTrainingPipeline.TrainingPipelineResult result = trainingPipeline.runPipeline();

            if (!result.success()) {
                log.error("Shrinkage model training FAILED: {}", result.errorMessage());
                recordFailureMetric();
                return;
            }

            log.info("Shrinkage model training SUCCEEDED: FPR={} TPR={} modelId={}",
                    String.format("%.3f", result.falsePositiveRate()),
                    String.format("%.3f", result.truePositiveRate()),
                    result.modelId());

            recordSuccessMetrics(result, (System.currentTimeMillis() - startTime) / 1000);

        } catch (Exception e) {
            log.error("Shrinkage model training failed with exception: {}", e.getMessage(), e);
            recordFailureMetric();
        }
    }

    private void recordSuccessMetrics(ShrinkageTrainingPipeline.TrainingPipelineResult result,
                                      long durationSec) {
        Counter.builder("zuqi_ai_shrinkage_training_success")
                .description("Successful shrinkage model training runs")
                .register(meterRegistry)
                .increment();

        meterRegistry.gauge("zuqi_ai_shrinkage_training_fpr",    result.falsePositiveRate());
        meterRegistry.gauge("zuqi_ai_shrinkage_training_tpr",    result.truePositiveRate());
        meterRegistry.gauge("zuqi_ai_shrinkage_training_duration_sec", (double) durationSec);
    }

    private void recordFailureMetric() {
        Counter.builder("zuqi_ai_shrinkage_training_failure")
                .description("Failed shrinkage model training runs")
                .register(meterRegistry)
                .increment();
    }
}
