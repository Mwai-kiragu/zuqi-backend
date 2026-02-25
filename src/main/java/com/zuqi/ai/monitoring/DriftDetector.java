package com.zuqi.ai.monitoring;

import com.zuqi.domain.ai.AIPrediction;
import com.zuqi.repository.AIPredictionRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Detects prediction distribution drift using Population Stability Index (PSI).
 *
 * <p>PSI measures how much the distribution of model predictions has shifted between
 * a historical reference window and a recent current window. A PSI > 0.2 indicates
 * significant drift and warrants model retraining investigation.
 *
 * <p><b>PSI Thresholds:</b>
 * <ul>
 *   <li>PSI &lt; 0.1 — STABLE: No significant change</li>
 *   <li>0.1 &le; PSI &lt; 0.2 — WARNING: Moderate change, monitor closely</li>
 *   <li>PSI &ge; 0.2 — DRIFT_DETECTED: Significant change, retraining recommended</li>
 * </ul>
 *
 * <p><b>Implementation Plan Reference:</b> Phase 6, Task 6.7
 * <p><b>Blueprint Reference:</b> plan.md Section 11 (Monitoring and Observability)
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
        prefix = "zuqi.ai.monitoring",
        name = "drift-detection-enabled",
        havingValue = "true",
        matchIfMissing = false
)
public class DriftDetector {

    private static final int NUM_BINS = 10;
    private static final int MIN_SAMPLES_REQUIRED = 10;

    private final AIPredictionRepository predictionRepository;
    private final MeterRegistry meterRegistry;

    @org.springframework.beans.factory.annotation.Value("${zuqi.ai.monitoring.psi-threshold:0.2}")
    private double psiThreshold;

    @org.springframework.beans.factory.annotation.Value("${zuqi.ai.monitoring.reference-window-days:90}")
    private int referenceWindowDays;

    @org.springframework.beans.factory.annotation.Value("${zuqi.ai.monitoring.current-window-days:14}")
    private int currentWindowDays;

    /**
     * Scheduled weekly drift check across all known model names.
     *
     * <p>Runs every Sunday at 06:00 by default. Collects distinct model names from
     * recent predictions and runs PSI drift detection for each.
     */
    @Scheduled(cron = "${zuqi.ai.monitoring.drift-check-cron:0 0 6 ? * SUN}")
    public void runWeeklyDriftCheck() {
        log.info("Starting weekly model drift detection check");

        // Fetch a broad sample of recent predictions to discover distinct model names
        List<AIPrediction> recentPredictions =
                predictionRepository.findAll(PageRequest.of(0, 1000)).getContent();

        List<String> distinctModelNames = recentPredictions.stream()
                .map(AIPrediction::getModelName)
                .distinct()
                .collect(Collectors.toList());

        if (distinctModelNames.isEmpty()) {
            log.info("No predictions found in repository — skipping drift check");
            return;
        }

        log.info("Running drift detection for {} model(s): {}", distinctModelNames.size(), distinctModelNames);

        for (String modelName : distinctModelNames) {
            try {
                DriftReport report = detectDrift(modelName);
                if (report.driftDetected()) {
                    log.warn("DRIFT DETECTED for model '{}': PSI={:.4f} (threshold={}) — retraining recommended",
                            modelName, report.psiScore(), psiThreshold);
                } else {
                    log.info("Model '{}' drift check complete: status={}, PSI={:.4f}",
                            modelName, report.status(), report.psiScore());
                }
            } catch (Exception ex) {
                log.error("Drift detection failed for model '{}': {}", modelName, ex.getMessage(), ex);
            }
        }

        log.info("Weekly drift detection check complete");
    }

    /**
     * Computes a drift report for a single model by comparing prediction score
     * distributions between the reference window and the current window.
     *
     * @param modelName the model name to analyse
     * @return {@link DriftReport} with PSI score, drift flag, and window sample counts
     */
    public DriftReport detectDrift(String modelName) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime currentWindowStart = now.minusDays(currentWindowDays);
        LocalDateTime referenceWindowStart = now.minusDays(referenceWindowDays);

        // Load all predictions within the reference window (excluding the current window)
        List<AIPrediction> allInReferenceRange =
                predictionRepository.findAll(PageRequest.of(0, 5000)).getContent();

        List<Double> referenceScores = allInReferenceRange.stream()
                .filter(p -> modelName.equals(p.getModelName()))
                .filter(p -> p.getCreatedAt() != null)
                .filter(p -> p.getCreatedAt().isAfter(referenceWindowStart)
                        && p.getCreatedAt().isBefore(currentWindowStart))
                .map(AIPrediction::getConfidenceScore)
                .filter(score -> score != null)
                .collect(Collectors.toList());

        // Load predictions within the current (recent) window
        List<Double> currentScores = allInReferenceRange.stream()
                .filter(p -> modelName.equals(p.getModelName()))
                .filter(p -> p.getCreatedAt() != null)
                .filter(p -> p.getCreatedAt().isAfter(currentWindowStart))
                .map(AIPrediction::getConfidenceScore)
                .filter(score -> score != null)
                .collect(Collectors.toList());

        // Guard: insufficient data in either window
        if (referenceScores.size() < MIN_SAMPLES_REQUIRED || currentScores.size() < MIN_SAMPLES_REQUIRED) {
            log.debug("Insufficient data for drift detection — model='{}', referenceSamples={}, currentSamples={}",
                    modelName, referenceScores.size(), currentScores.size());
            recordPsiMetric(modelName, 0.0);
            return new DriftReport(
                    modelName,
                    false,
                    0.0,
                    "INSUFFICIENT_DATA",
                    referenceScores.size(),
                    currentScores.size(),
                    now
            );
        }

        double psi = computePsi(referenceScores, currentScores);
        boolean driftDetected = psi >= psiThreshold;

        String status;
        if (psi < 0.1) {
            status = "STABLE";
        } else if (psi < psiThreshold) {
            status = "WARNING";
        } else {
            status = "DRIFT_DETECTED";
        }

        recordPsiMetric(modelName, psi);

        return new DriftReport(
                modelName,
                driftDetected,
                psi,
                status,
                referenceScores.size(),
                currentScores.size(),
                now
        );
    }

    /**
     * Computes the Population Stability Index (PSI) between two score distributions.
     *
     * <p>Scores are expected to be in [0.0, 1.0]. They are binned into 10 equal-width
     * buckets. Each bucket contributes:
     * <pre>
     *   psi_bucket = (actual_pct - expected_pct) * ln(actual_pct / expected_pct)
     * </pre>
     * A small epsilon (1e-10) is added to prevent division by zero and log(0).
     *
     * @param reference historical (reference) prediction scores
     * @param current   recent (current) prediction scores
     * @return total PSI value
     */
    private double computePsi(List<Double> reference, List<Double> current) {
        int n = reference.size();
        int m = current.size();

        double[] expectedCounts = new double[NUM_BINS];
        double[] actualCounts = new double[NUM_BINS];

        // Bin reference (expected) scores
        for (double score : reference) {
            int bin = scoreToBin(score);
            expectedCounts[bin]++;
        }

        // Bin current (actual) scores
        for (double score : current) {
            int bin = scoreToBin(score);
            actualCounts[bin]++;
        }

        double psi = 0.0;
        for (int i = 0; i < NUM_BINS; i++) {
            double expectedPct = expectedCounts[i] / n;
            double actualPct = actualCounts[i] / m;

            // Apply epsilon to avoid log(0) or division by zero
            double ep = expectedPct + 1e-10;
            double ap = actualPct + 1e-10;

            psi += (ap - ep) * Math.log(ap / ep);
        }

        return psi;
    }

    /**
     * Maps a score in [0.0, 1.0] to a bin index in [0, NUM_BINS - 1].
     *
     * @param score prediction confidence score
     * @return bin index
     */
    private int scoreToBin(double score) {
        // Clamp score to [0.0, 1.0]
        double clamped = Math.max(0.0, Math.min(1.0, score));
        int bin = (int) (clamped * NUM_BINS);
        // Edge case: score == 1.0 maps to bin index 10; clamp to last bucket
        return Math.min(bin, NUM_BINS - 1);
    }

    /**
     * Records the PSI value as a Micrometer gauge metric.
     *
     * @param modelName the model whose PSI is being recorded
     * @param psi       the computed PSI value
     */
    private void recordPsiMetric(String modelName, double psi) {
        AtomicReference<Double> psiRef = new AtomicReference<>(psi);
        meterRegistry.gauge(
                "zuqi_ai_model_drift_psi",
                java.util.List.of(io.micrometer.core.instrument.Tag.of("model", modelName)),
                psiRef,
                AtomicReference::get
        );
    }

    // -------------------------------------------------------------------------
    // Inner record — DriftReport
    // -------------------------------------------------------------------------

    /**
     * Immutable result of a single model drift check.
     *
     * @param modelName        name of the model that was checked
     * @param driftDetected    {@code true} if PSI exceeded the configured threshold
     * @param psiScore         computed Population Stability Index
     * @param status           one of: STABLE, WARNING, DRIFT_DETECTED, INSUFFICIENT_DATA
     * @param referenceSamples number of predictions in the historical reference window
     * @param currentSamples   number of predictions in the recent current window
     * @param checkedAt        timestamp when the check was performed
     */
    public record DriftReport(
            String modelName,
            boolean driftDetected,
            double psiScore,
            String status,
            int referenceSamples,
            int currentSamples,
            LocalDateTime checkedAt
    ) {}
}
