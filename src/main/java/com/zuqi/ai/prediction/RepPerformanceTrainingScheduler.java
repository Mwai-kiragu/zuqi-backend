package com.zuqi.ai.prediction;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Scheduled weekly retraining of the sales rep performance predictor.
 *
 * Blueprint reference: implementation_plan.md Phase 4, Step 7b
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
        prefix = "zuqi.ai.prediction",
        name = "training-enabled",
        havingValue = "true",
        matchIfMissing = false
)
public class RepPerformanceTrainingScheduler {

    private final RepPerformanceTrainingPipeline trainingPipeline;
    private final MeterRegistry                  meterRegistry;

    @Scheduled(cron = "${zuqi.ai.prediction.rep-training-cron:0 30 3 ? * SUN}")
    public void weeklyTraining() {
        log.info("{}", "=".repeat(80));
        log.info("WEEKLY REP PERFORMANCE MODEL TRAINING - STARTED");
        log.info("{}", "=".repeat(80));

        try {
            RepPerformanceTrainingPipeline.TrainingPipelineResult result = trainingPipeline.runPipeline();

            if (!result.success()) {
                log.error("Rep performance training FAILED: {}", result.errorMessage());
                recordFailureMetric();
                return;
            }

            log.info("Rep performance training SUCCEEDED: R²={} RMSE={} modelId={}",
                    String.format("%.3f", result.r2()),
                    String.format("%.2f", result.rmse()),
                    result.modelId());

            recordSuccessMetrics(result);

        } catch (Exception e) {
            log.error("Rep performance training exception: {}", e.getMessage(), e);
            recordFailureMetric();
        }
    }

    private void recordSuccessMetrics(RepPerformanceTrainingPipeline.TrainingPipelineResult result) {
        Counter.builder("zuqi_ai_rep_performance_training_success")
                .description("Successful rep performance model training runs")
                .register(meterRegistry)
                .increment();
        meterRegistry.gauge("zuqi_ai_rep_performance_training_r2",   result.r2());
        meterRegistry.gauge("zuqi_ai_rep_performance_training_rmse", result.rmse());
    }

    private void recordFailureMetric() {
        Counter.builder("zuqi_ai_rep_performance_training_failure")
                .description("Failed rep performance model training runs")
                .register(meterRegistry)
                .increment();
    }
}
