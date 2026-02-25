package com.zuqi.ai.monitoring;

import com.zuqi.domain.ai.AIPrediction;
import com.zuqi.repository.AIPredictionRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DriftDetector} — PSI-based model drift detection.
 *
 * Uses {@link SimpleMeterRegistry} (no Spring context) so Micrometer gauge
 * registration works without a full application context.
 *
 * Blueprint reference: implementation_plan.md Phase 6, Task 6.7
 */
@SuppressWarnings("DataFlowIssue")
@ExtendWith(MockitoExtension.class)
class DriftDetectorTest {

    @Mock
    private AIPredictionRepository predictionRepository;

    private DriftDetector driftDetector;

    private static final String MODEL_NAME = "test_credit_model";

    /**
     * The DriftDetector filters predictions by date:
     * - reference window: [now - referenceWindowDays, now - currentWindowDays)
     * - current window:   [now - currentWindowDays, now)
     *
     * We set referenceWindowDays=90 and currentWindowDays=14.
     * Reference predictions must have createdAt between 90 and 14 days ago.
     * Current predictions must have createdAt within the last 14 days.
     */
    private static final int REFERENCE_WINDOW_DAYS = 90;
    private static final int CURRENT_WINDOW_DAYS   = 14;
    private static final double PSI_THRESHOLD      = 0.2;

    @BeforeEach
    void setUp() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        driftDetector = new DriftDetector(predictionRepository, meterRegistry);

        ReflectionTestUtils.setField(driftDetector, "psiThreshold",       PSI_THRESHOLD);
        ReflectionTestUtils.setField(driftDetector, "referenceWindowDays", REFERENCE_WINDOW_DAYS);
        ReflectionTestUtils.setField(driftDetector, "currentWindowDays",   CURRENT_WINDOW_DAYS);
    }

    // ── Test 1: Insufficient data ────────────────────────────────────────────

    @Test
    void detectDrift_insufficientData_returnsInsufficientDataStatus() {
        // Repository returns an empty page — no predictions at all
        when(predictionRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        DriftDetector.DriftReport report = driftDetector.detectDrift(MODEL_NAME);

        assertThat(report.status()).isEqualTo("INSUFFICIENT_DATA");
        assertThat(report.driftDetected()).isFalse();
        assertThat(report.psiScore()).isEqualTo(0.0);
        assertThat(report.modelName()).isEqualTo(MODEL_NAME);
    }

    // ── Test 2: Identical distributions → PSI near zero ─────────────────────

    @Test
    void detectDrift_identicalDistributions_psiNearZero() {
        // Reference: 20 predictions uniformly distributed in 0..1, 60 days ago
        // Current:   20 predictions uniformly distributed in 0..1, 7 days ago
        List<AIPrediction> allPredictions = new ArrayList<>();
        allPredictions.addAll(buildPredictions(MODEL_NAME, uniformScores(20), daysAgo(60)));
        allPredictions.addAll(buildPredictions(MODEL_NAME, uniformScores(20), daysAgo(7)));

        when(predictionRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(allPredictions));

        DriftDetector.DriftReport report = driftDetector.detectDrift(MODEL_NAME);

        // Identical uniform distributions → PSI should be very close to 0
        assertThat(report.psiScore()).isLessThan(0.1);
        assertThat(report.status()).isEqualTo("STABLE");
        assertThat(report.driftDetected()).isFalse();
    }

    // ── Test 3: Completely different distributions → high PSI ────────────────

    @Test
    void detectDrift_completelyDifferentDistributions_highPsi() {
        // Reference: all scores in [0.0, 0.1] (low bucket), 60 days ago
        // Current:   all scores in [0.9, 1.0] (high bucket), 7 days ago
        List<Double> referenceScores = buildScoresInRange(20, 0.01, 0.09);
        List<Double> currentScores   = buildScoresInRange(20, 0.91, 0.99);

        List<AIPrediction> allPredictions = new ArrayList<>();
        allPredictions.addAll(buildPredictions(MODEL_NAME, referenceScores, daysAgo(60)));
        allPredictions.addAll(buildPredictions(MODEL_NAME, currentScores,   daysAgo(7)));

        when(predictionRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(allPredictions));

        DriftDetector.DriftReport report = driftDetector.detectDrift(MODEL_NAME);

        // Maximum distribution shift: PSI must be well above the 0.2 threshold
        assertThat(report.psiScore()).isGreaterThan(PSI_THRESHOLD);
        assertThat(report.driftDetected()).isTrue();
    }

    // ── Test 4: Stable (similar) distributions → no alarm ───────────────────

    @Test
    void detectDrift_stableDistribution_noAlarm() {
        // Both windows have similar uniform distributions — slight variation only
        List<Double> referenceScores = buildScoresInRange(20, 0.2, 0.8);
        List<Double> currentScores   = buildScoresInRange(20, 0.2, 0.8);

        List<AIPrediction> allPredictions = new ArrayList<>();
        allPredictions.addAll(buildPredictions(MODEL_NAME, referenceScores, daysAgo(50)));
        allPredictions.addAll(buildPredictions(MODEL_NAME, currentScores,   daysAgo(5)));

        when(predictionRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(allPredictions));

        DriftDetector.DriftReport report = driftDetector.detectDrift(MODEL_NAME);

        assertThat(report.driftDetected()).isFalse();
        assertThat(report.status()).isIn("STABLE", "WARNING");
    }

    // ── Test 5: Shifted distribution → drift detected ────────────────────────

    @Test
    void detectDrift_shiftedDistribution_driftDetected() {
        // Reference: mid-range scores [0.3, 0.6], 60 days ago
        // Current:   high-range scores [0.8, 1.0], 7 days ago — significant shift
        List<Double> referenceScores = buildScoresInRange(30, 0.3, 0.6);
        List<Double> currentScores   = buildScoresInRange(30, 0.8, 1.0);

        List<AIPrediction> allPredictions = new ArrayList<>();
        allPredictions.addAll(buildPredictions(MODEL_NAME, referenceScores, daysAgo(60)));
        allPredictions.addAll(buildPredictions(MODEL_NAME, currentScores,   daysAgo(7)));

        when(predictionRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(allPredictions));

        DriftDetector.DriftReport report = driftDetector.detectDrift(MODEL_NAME);

        assertThat(report.driftDetected()).isTrue();
        assertThat(report.status()).isEqualTo("DRIFT_DETECTED");
        assertThat(report.psiScore()).isGreaterThan(PSI_THRESHOLD);
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    /**
     * Build a list of mocked AIPrediction objects for the given model name,
     * confidence scores, and createdAt timestamp.
     */
    private List<AIPrediction> buildPredictions(String modelName,
                                                 List<Double> scores,
                                                 LocalDateTime createdAt) {
        List<AIPrediction> predictions = new ArrayList<>();
        for (Double score : scores) {
            AIPrediction pred = mock(AIPrediction.class);
            when(pred.getModelName()).thenReturn(modelName);
            when(pred.getConfidenceScore()).thenReturn(score);
            when(pred.getCreatedAt()).thenReturn(createdAt);
            predictions.add(pred);
        }
        return predictions;
    }

    /**
     * Return a list of {@code count} uniformly spaced scores in [0.05, 0.95].
     * Covers all 10 PSI bins roughly evenly.
     */
    private List<Double> uniformScores(int count) {
        List<Double> scores = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            scores.add(0.05 + (0.9 * i / Math.max(count - 1, 1)));
        }
        return scores;
    }

    /**
     * Build {@code count} evenly-spaced scores in [min, max].
     */
    private List<Double> buildScoresInRange(int count, double min, double max) {
        List<Double> scores = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double fraction = (count == 1) ? 0.5 : (double) i / (count - 1);
            scores.add(min + fraction * (max - min));
        }
        return scores;
    }

    /**
     * Returns a timestamp that falls inside the reference window
     * (older than currentWindowDays but within referenceWindowDays).
     */
    private LocalDateTime daysAgo(int days) {
        return LocalDateTime.now().minusDays(days);
    }
}
