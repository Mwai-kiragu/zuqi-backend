package com.zuqi.ai.demand;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Scheduled service for weekly demand model training.
 *
 * Execution Strategy:
 * - Runs weekly (every Sunday at 2:00 AM EAT)
 * - Trains XGBoost regressor on synthetic order data
 * - Evaluates model with quality gate (R² > 0.70)
 * - Promotes model to ACTIVE if quality gates pass
 * - Trained model is used by DemandForecastJob (3:00 AM) and OrderSuggestionService
 *
 * Data Strategy:
 * - Currently uses synthetic data (SyntheticOrderDataGenerator)
 * - Future: Blend with real order history as data accumulates
 * - Training size: 100 merchants, 50 products, 52 weeks of history
 *
 * Blueprint: implementation_plan.md Phase 3 Task 3.4
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
        prefix = "zuqi.ai.demand-forecasting",
        name = "training-enabled",
        havingValue = "true",
        matchIfMissing = false
)
public class DemandModelTrainingScheduler {

    private final DemandModelTrainingPipeline trainingPipeline;
    private final MeterRegistry meterRegistry;

    // Training data configuration (adjust as needed)
    private static final int NUM_MERCHANTS = 100;
    private static final int NUM_PRODUCTS = 50;
    private static final int NUM_WEEKS = 52; // 1 year of synthetic data

    /**
     * Weekly demand model training job.
     * Runs every Sunday at 2:00 AM (before forecast job at 3:00 AM).
     * Cron: 0 0 2 ? * SUN
     */
    @Scheduled(cron = "${zuqi.ai.demand-forecasting.training-cron:0 0 2 ? * SUN}")
    public void weeklyTraining() {
        log.info("=".repeat(80));
        log.info("WEEKLY DEMAND MODEL TRAINING - STARTED");
        log.info("=" .repeat(80));

        long startTime = System.currentTimeMillis();

        try {
            // Run training pipeline
            log.info("Training demand model with {} merchants, {} products, {} weeks of data",
                    NUM_MERCHANTS, NUM_PRODUCTS, NUM_WEEKS);

            DemandModelTrainingPipeline.TrainingPipelineResult result =
                    trainingPipeline.runPipeline(NUM_MERCHANTS, NUM_PRODUCTS, NUM_WEEKS);

            if (!result.success()) {
                log.error("Demand model training FAILED: {}", result.errorMessage());
                recordFailureMetric();
                return;
            }

            // Log success metrics
            log.info("Demand model training SUCCEEDED:");
            log.info("  - R²: {:.3f}", result.evaluation().r2());
            log.info("  - RMSE: {:.2f}", result.evaluation().rmse());
            log.info("  - MAE: {:.2f}", result.evaluation().mae());
            log.info("  - Explained Variance: {:.3f}", result.evaluation().explainedVariance());
            log.info("  - Model ID: {}", result.modelId());
            log.info("  - Training examples: {}", result.trainSize());
            log.info("  - Test examples: {}", result.testSize());

            long durationSec = (System.currentTimeMillis() - startTime) / 1000;
            log.info("=" .repeat(80));
            log.info("WEEKLY TRAINING COMPLETE - Duration: {}s", durationSec);
            log.info("=" .repeat(80));

            // Record success metrics
            recordSuccessMetrics(result, durationSec);

        } catch (Exception e) {
            log.error("Demand model training failed with exception: {}", e.getMessage(), e);
            recordFailureMetric();
        }
    }

    /**
     * Record success metrics to Prometheus.
     */
    private void recordSuccessMetrics(DemandModelTrainingPipeline.TrainingPipelineResult result,
                                       long durationSec) {
        Counter.builder("zuqi_ai_demand_training_success")
                .description("Successful demand model training runs")
                .tag("job", "demand_training")
                .register(meterRegistry)
                .increment();

        meterRegistry.gauge("zuqi_ai_demand_training_r2", result.evaluation().r2());
        meterRegistry.gauge("zuqi_ai_demand_training_rmse", result.evaluation().rmse());
        meterRegistry.gauge("zuqi_ai_demand_training_duration_sec", durationSec);
    }

    /**
     * Record failure metric to Prometheus.
     */
    private void recordFailureMetric() {
        Counter.builder("zuqi_ai_demand_training_failure")
                .description("Failed demand model training runs")
                .tag("job", "demand_training")
                .register(meterRegistry)
                .increment();
    }
}
