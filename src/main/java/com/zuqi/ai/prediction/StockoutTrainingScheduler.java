package com.zuqi.ai.prediction;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Scheduled weekly retraining of the stockout predictor.
 *
 * Blueprint reference: implementation_plan.md Phase 4, Step 7a
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
public class StockoutTrainingScheduler {

    private final StockoutTrainingPipeline trainingPipeline;
    private final MeterRegistry            meterRegistry;

    @Scheduled(cron = "${zuqi.ai.prediction.stockout-training-cron:0 0 3 ? * SUN}")
    public void weeklyTraining() {
        log.info("{}", "=".repeat(80));
        log.info("WEEKLY STOCKOUT PREDICTOR TRAINING - STARTED");
        log.info("{}", "=".repeat(80));

        try {
            StockoutTrainingPipeline.TrainingPipelineResult result = trainingPipeline.runPipeline();

            if (!result.success()) {
                log.error("Stockout predictor training FAILED: {}", result.errorMessage());
                recordFailureMetric();
                return;
            }

            log.info("Stockout predictor training SUCCEEDED: AUC={} accuracy={} modelId={}",
                    String.format("%.3f", result.aucRoc()),
                    String.format("%.3f", result.accuracy()),
                    result.modelId());

            recordSuccessMetrics(result);

        } catch (Exception e) {
            log.error("Stockout predictor training exception: {}", e.getMessage(), e);
            recordFailureMetric();
        }
    }

    private void recordSuccessMetrics(StockoutTrainingPipeline.TrainingPipelineResult result) {
        Counter.builder("zuqi_ai_stockout_training_success")
                .description("Successful stockout predictor training runs")
                .register(meterRegistry)
                .increment();
        meterRegistry.gauge("zuqi_ai_stockout_training_auc",      result.aucRoc());
        meterRegistry.gauge("zuqi_ai_stockout_training_accuracy", result.accuracy());
    }

    private void recordFailureMetric() {
        Counter.builder("zuqi_ai_stockout_training_failure")
                .description("Failed stockout predictor training runs")
                .register(meterRegistry)
                .increment();
    }
}
