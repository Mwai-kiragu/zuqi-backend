package com.zuqi.ai.monitoring;

import com.zuqi.domain.ai.AIPrediction;
import com.zuqi.domain.ai.AIModelRegistry;
import com.zuqi.domain.ai.ModelStatus;
import com.zuqi.repository.AIPredictionRepository;
import com.zuqi.repository.AIModelRegistryRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Tracks and exposes model performance metrics over time.
 *
 * <p>Runs on a daily schedule to compute per-model prediction statistics (volume,
 * average confidence score, confidence distribution) and expose them as Micrometer
 * gauges for Prometheus scraping and Grafana dashboards.
 *
 * <p>Also exposes on-demand {@link #trackModelMetrics(String)} and
 * {@link #getAllModelMetrics()} methods for programmatic access (e.g., from an
 * AI health REST endpoint).
 *
 * <p><b>Implementation Plan Reference:</b> Phase 6, Task 6.7
 * <p><b>Blueprint Reference:</b> plan.md Section 11 (Monitoring and Observability)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ModelPerformanceTracker {

    /** Canonical model names managed by the Zuqi AI system. */
    private static final List<String> KNOWN_MODELS = List.of(
            "credit_classifier",
            "demand_forecaster",
            "shrinkage_detector",
            "payment_anomaly_detector",
            "stockout_predictor",
            "rep_performance_predictor",
            "payment_distress_classifier"
    );

    /** Look-back window for daily performance metrics. */
    private static final int LOOKBACK_DAYS = 7;

    /** Confidence score boundary between medium and high confidence. */
    private static final double HIGH_CONFIDENCE_THRESHOLD = 0.7;

    /** Confidence score boundary between low and medium confidence. */
    private static final double MEDIUM_CONFIDENCE_THRESHOLD = 0.4;

    /** Max predictions fetched per model tracking call. */
    private static final int MAX_FETCH_SIZE = 10_000;

    private final AIPredictionRepository predictionRepository;
    private final AIModelRegistryRepository modelRegistryRepository;
    private final MeterRegistry meterRegistry;

    // -------------------------------------------------------------------------
    // Scheduled daily tracking
    // -------------------------------------------------------------------------

    /**
     * Runs every day at 05:00 (default) to compute and publish model performance
     * metrics for all known models.
     */
    @Scheduled(cron = "${zuqi.ai.monitoring.performance-tracking-cron:0 0 5 * * *}")
    public void trackDailyPerformance() {
        log.info("Tracking daily model performance metrics for {} known models", KNOWN_MODELS.size());

        for (String modelName : KNOWN_MODELS) {
            try {
                ModelMetrics metrics = trackModelMetrics(modelName);
                log.debug("Performance tracked — model='{}', predictions7d={}, avgScore={:.4f}",
                        modelName, metrics.totalPredictions7d(), metrics.avgPredictionScore());
            } catch (Exception ex) {
                log.error("Failed to track performance for model '{}': {}", modelName, ex.getMessage(), ex);
            }
        }

        log.info("Daily model performance tracking complete");
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Computes and publishes performance metrics for a single model.
     *
     * <p>Fetches up to {@value #MAX_FETCH_SIZE} prediction records, filters to
     * those belonging to {@code modelName} within the last {@value #LOOKBACK_DAYS}
     * days, and derives:
     * <ul>
     *   <li>Total prediction count in the window</li>
     *   <li>Average confidence score</li>
     *   <li>Breakdown by confidence tier (high / medium / low)</li>
     * </ul>
     *
     * <p>Results are exposed as Micrometer gauges:
     * <ul>
     *   <li>{@code zuqi_ai_model_prediction_count_7d{model=...}}</li>
     *   <li>{@code zuqi_ai_model_avg_score{model=...}}</li>
     * </ul>
     *
     * @param modelName the model whose metrics are to be computed
     * @return {@link ModelMetrics} containing the computed statistics
     */
    public ModelMetrics trackModelMetrics(String modelName) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime since = now.minusDays(LOOKBACK_DAYS);

        // Fetch a broad page of predictions and filter locally — avoids adding
        // new repository methods while keeping the query simple and testable.
        List<AIPrediction> recentPredictions =
                predictionRepository.findAll(PageRequest.of(0, MAX_FETCH_SIZE)).getContent()
                        .stream()
                        .filter(p -> modelName.equals(p.getModelName()))
                        .filter(p -> p.getCreatedAt() != null && p.getCreatedAt().isAfter(since))
                        .collect(Collectors.toList());

        long totalPredictions = recentPredictions.size();

        // Extract only non-null confidence scores for numeric aggregation
        List<Double> scores = recentPredictions.stream()
                .map(AIPrediction::getConfidenceScore)
                .filter(score -> score != null)
                .collect(Collectors.toList());

        double avgScore = scores.isEmpty()
                ? 0.0
                : scores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

        long highConfidence = scores.stream()
                .filter(s -> s > HIGH_CONFIDENCE_THRESHOLD)
                .count();

        long mediumConfidence = scores.stream()
                .filter(s -> s >= MEDIUM_CONFIDENCE_THRESHOLD && s <= HIGH_CONFIDENCE_THRESHOLD)
                .count();

        long lowConfidence = scores.stream()
                .filter(s -> s < MEDIUM_CONFIDENCE_THRESHOLD)
                .count();

        // Publish Micrometer gauges
        recordGauge("zuqi_ai_model_prediction_count_7d", modelName, (double) totalPredictions);
        recordGauge("zuqi_ai_model_avg_score", modelName, avgScore);

        return new ModelMetrics(
                modelName,
                totalPredictions,
                avgScore,
                highConfidence,
                mediumConfidence,
                lowConfidence,
                now
        );
    }

    /**
     * Computes and returns {@link ModelMetrics} for every model in {@link #KNOWN_MODELS}.
     *
     * @return list of metrics snapshots, one per known model
     */
    public List<ModelMetrics> getAllModelMetrics() {
        return KNOWN_MODELS.stream()
                .map(modelName -> {
                    try {
                        return trackModelMetrics(modelName);
                    } catch (Exception ex) {
                        log.error("Failed to retrieve metrics for model '{}': {}", modelName, ex.getMessage(), ex);
                        // Return a zero-valued record so callers always get a full list
                        return new ModelMetrics(modelName, 0L, 0.0, 0L, 0L, 0L, LocalDateTime.now());
                    }
                })
                .collect(Collectors.toList());
    }

    /**
     * Returns a summary of active models registered in the model registry.
     *
     * <p>Useful for health-check endpoints that need to surface which models are
     * currently in production ({@link ModelStatus#ACTIVE}).
     *
     * @return list of active {@link AIModelRegistry} entries
     */
    public List<AIModelRegistry> getActiveModels() {
        return modelRegistryRepository.findByStatus(ModelStatus.ACTIVE);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Records a double value as a Micrometer gauge tagged with the model name.
     *
     * <p>Uses an {@link AtomicReference} to satisfy the gauge API requirement for
     * a mutable number holder.
     *
     * @param metricName Micrometer metric name
     * @param modelName  value for the {@code model} tag
     * @param value      the gauge value to record
     */
    private void recordGauge(String metricName, String modelName, double value) {
        AtomicReference<Double> ref = new AtomicReference<>(value);
        meterRegistry.gauge(
                metricName,
                List.of(io.micrometer.core.instrument.Tag.of("model", modelName)),
                ref,
                AtomicReference::get
        );
    }

    // -------------------------------------------------------------------------
    // Inner record — ModelMetrics
    // -------------------------------------------------------------------------

    /**
     * Immutable snapshot of per-model performance statistics over the last 7 days.
     *
     * @param modelName             name of the model
     * @param totalPredictions7d    total predictions recorded in the last 7 days
     * @param avgPredictionScore    mean confidence score over the window (0.0 if none)
     * @param highConfidenceCount   predictions with confidence &gt; 0.7
     * @param mediumConfidenceCount predictions with confidence in [0.4, 0.7]
     * @param lowConfidenceCount    predictions with confidence &lt; 0.4
     * @param measuredAt            timestamp when the metrics were computed
     */
    public record ModelMetrics(
            String modelName,
            long totalPredictions7d,
            double avgPredictionScore,
            long highConfidenceCount,
            long mediumConfidenceCount,
            long lowConfidenceCount,
            LocalDateTime measuredAt
    ) {}
}
