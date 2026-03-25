package com.zuqi.ai.synthetic;

import com.zuqi.ai.event.DataPhaseTransitionEvent;
import com.zuqi.domain.ai.AIDataPhase;
import com.zuqi.domain.ai.DataPhase;
import com.zuqi.domain.distributor.Distributor;
import com.zuqi.repository.AIDataPhaseRepository;
import com.zuqi.repository.DistributorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks and evaluates the SYNTHETIC → HYBRID → REAL data maturity phase for every
 * (model, distributor) pair.
 *
 * <h3>Phase semantics</h3>
 * <ul>
 *   <li>SYNTHETIC — model trained entirely on generated in-memory data</li>
 *   <li>HYBRID    — blend of synthetic and real data (real count ≥ hybrid threshold)</li>
 *   <li>REAL      — predominantly real data (real count ≥ real threshold AND ratio ≥ 80%)</li>
 * </ul>
 *
 * <h3>Transition thresholds</h3>
 * Each of the 9 ML models has its own threshold configuration (see the static
 * {@code THRESHOLDS} map). Unknown model names fall back to
 * {@link TransitionThreshold#DEFAULT_THRESHOLD}.
 *
 * <h3>Usage pattern</h3>
 * <ol>
 *   <li>After every training run, call {@link #updateCounts} with the real and
 *       synthetic example counts used.</li>
 *   <li>Then call {@link #evaluatePhase} to advance the phase if thresholds are met.</li>
 *   <li>A {@link DataPhaseTransitionEvent} is published whenever the phase advances.</li>
 * </ol>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DataPhaseTracker {

    // ── Model name constants ────────────────────────────────────────────────

    // ── Phase 1 model names ─────────────────────────────────────────────────

    public static final String MODEL_DEMAND_FORECASTER           = "demand_forecaster";
    public static final String MODEL_CREDIT_CLASSIFIER           = "credit_classifier";
    public static final String MODEL_STOCKOUT_PREDICTOR          = "stockout_predictor";
    public static final String MODEL_SHRINKAGE_DETECTOR          = "shrinkage_detector";
    public static final String MODEL_PAYMENT_ANOMALY_DETECTOR    = "payment_anomaly_detector";
    public static final String MODEL_DATA_QUALITY_DETECTOR       = "data_quality_detector";
    public static final String MODEL_REP_PERFORMANCE_PREDICTOR   = "rep_performance_predictor";
    public static final String MODEL_CREDIT_LIMIT_REGRESSOR      = "credit_limit_regressor";
    public static final String MODEL_PAYMENT_DISTRESS_CLASSIFIER = "payment_distress_classifier";

    // ── Phase 2 model names (8 new models) ─────────────────────────────────

    public static final String MODEL_BANK_RECON_MATCHER          = "bank_recon_matcher";
    public static final String MODEL_CASH_FLOW_PREDICTOR         = "cash_flow_predictor";
    public static final String MODEL_CUSTOMER_SEGMENTER          = "customer_segmenter";
    public static final String MODEL_CUSTOMER_HEALTH_SCORER      = "customer_health_scorer";
    public static final String MODEL_CUSTOMER_CLV_PREDICTOR      = "customer_clv_predictor";
    public static final String MODEL_CHURN_PREDICTOR             = "churn_predictor";
    public static final String MODEL_REORDER_OPTIMIZER           = "reorder_optimizer";
    public static final String MODEL_EXPIRY_RISK_PREDICTOR       = "expiry_risk_predictor";

    // ── Transition threshold registry ───────────────────────────────────────

    /**
     * Per-model thresholds derived from Synthetic Data Strategy §2.2.
     *
     * <p>Hybrid threshold = minimum real training examples to enter HYBRID.
     * Real threshold = minimum real training examples + ratio ≥ 80% to enter REAL.
     */
    private static final Map<String, TransitionThreshold> THRESHOLDS = Map.of(
            MODEL_DEMAND_FORECASTER,
                new TransitionThreshold(MODEL_DEMAND_FORECASTER,          50,  200, 0.80),
            MODEL_CREDIT_CLASSIFIER,
                new TransitionThreshold(MODEL_CREDIT_CLASSIFIER,         200,  500, 0.80),
            MODEL_STOCKOUT_PREDICTOR,
                new TransitionThreshold(MODEL_STOCKOUT_PREDICTOR,         50,  200, 0.80),
            MODEL_SHRINKAGE_DETECTOR,
                new TransitionThreshold(MODEL_SHRINKAGE_DETECTOR,         50,  200, 0.80),
            MODEL_PAYMENT_ANOMALY_DETECTOR,
                new TransitionThreshold(MODEL_PAYMENT_ANOMALY_DETECTOR,  100,  300, 0.80),
            MODEL_DATA_QUALITY_DETECTOR,
                new TransitionThreshold(MODEL_DATA_QUALITY_DETECTOR,      50,  200, 0.80),
            MODEL_REP_PERFORMANCE_PREDICTOR,
                new TransitionThreshold(MODEL_REP_PERFORMANCE_PREDICTOR, 100,  300, 0.80),
            MODEL_CREDIT_LIMIT_REGRESSOR,
                new TransitionThreshold(MODEL_CREDIT_LIMIT_REGRESSOR,    200,  500, 0.80)
            // ninth entry added via Map.copyOf to stay under Map.of(10) overload limit
    );

    // Map.of has a max of 10 pairs; accumulate all entries via HashMap
    private static final Map<String, TransitionThreshold> ALL_THRESHOLDS;
    static {
        java.util.HashMap<String, TransitionThreshold> m = new java.util.HashMap<>(THRESHOLDS);
        // Phase 1 — 9th entry (Map.of limit workaround)
        m.put(MODEL_PAYMENT_DISTRESS_CLASSIFIER,
                new TransitionThreshold(MODEL_PAYMENT_DISTRESS_CLASSIFIER, 100, 300, 0.80));
        // Phase 2 — 8 new models
        m.put(MODEL_BANK_RECON_MATCHER,
                new TransitionThreshold(MODEL_BANK_RECON_MATCHER,      100, 300, 0.80));
        m.put(MODEL_CASH_FLOW_PREDICTOR,
                new TransitionThreshold(MODEL_CASH_FLOW_PREDICTOR,      50, 200, 0.80));
        m.put(MODEL_CUSTOMER_SEGMENTER,
                new TransitionThreshold(MODEL_CUSTOMER_SEGMENTER,      200, 500, 0.80));
        m.put(MODEL_CUSTOMER_HEALTH_SCORER,
                new TransitionThreshold(MODEL_CUSTOMER_HEALTH_SCORER,  200, 500, 0.80));
        m.put(MODEL_CUSTOMER_CLV_PREDICTOR,
                new TransitionThreshold(MODEL_CUSTOMER_CLV_PREDICTOR,  100, 300, 0.80));
        m.put(MODEL_CHURN_PREDICTOR,
                new TransitionThreshold(MODEL_CHURN_PREDICTOR,         200, 500, 0.80));
        m.put(MODEL_REORDER_OPTIMIZER,
                new TransitionThreshold(MODEL_REORDER_OPTIMIZER,        50, 200, 0.80));
        m.put(MODEL_EXPIRY_RISK_PREDICTOR,
                new TransitionThreshold(MODEL_EXPIRY_RISK_PREDICTOR,    50, 200, 0.80));
        ALL_THRESHOLDS = java.util.Collections.unmodifiableMap(m);
    }

    // ── Dependencies ────────────────────────────────────────────────────────

    private final AIDataPhaseRepository  phaseRepository;
    private final DistributorRepository  distributorRepository;
    private final ApplicationEventPublisher eventPublisher;

    // ── Public API ──────────────────────────────────────────────────────────

    /**
     * Return the current data phase for the given model and distributor.
     * If no record exists, SYNTHETIC is assumed (the initial phase for all models).
     */
    @Transactional(readOnly = true)
    public DataPhase getPhase(String modelName, UUID distributorId) {
        return findOrBuildDefault(modelName, distributorId).getCurrentPhase();
    }

    /**
     * Return the fraction of real training examples for the given model and distributor
     * (0.0 = all synthetic, 1.0 = all real). Returns 0.0 if no record exists.
     */
    @Transactional(readOnly = true)
    public double getRealDataRatio(String modelName, UUID distributorId) {
        return findOrBuildDefault(modelName, distributorId).getRealDataRatio();
    }

    /**
     * Accumulate additional real and synthetic example counts into the tracking record.
     *
     * <p>Counts are additive — each call adds to the running totals. The real-data ratio
     * is recomputed from the updated totals.
     *
     * @param modelName           model identifier
     * @param distributorId       distributor scope (null = global)
     * @param additionalReal      number of real examples used in the latest training run
     * @param additionalSynthetic number of synthetic examples used in the latest training run
     */
    @Transactional
    public void updateCounts(String modelName, UUID distributorId,
                             int additionalReal, int additionalSynthetic) {
        AIDataPhase phase = findOrCreate(modelName, distributorId);

        int newReal      = phase.getRealDataCount()      + additionalReal;
        int newSynthetic = phase.getSyntheticDataCount() + additionalSynthetic;
        int total        = newReal + newSynthetic;
        double ratio     = total > 0 ? (double) newReal / total : 0.0;

        phase.setRealDataCount(newReal);
        phase.setSyntheticDataCount(newSynthetic);
        phase.setRealDataRatio(ratio);

        phaseRepository.save(phase);
        log.debug("[DataPhase] updateCounts {}/{} — real={}, synthetic={}, ratio={}",
                modelName, distributorId, newReal, newSynthetic,
                String.format("%.3f", ratio));
    }

    /**
     * Check whether the current counts satisfy any phase transition criteria and
     * advance the phase if they do.
     *
     * <p>Publishes a {@link DataPhaseTransitionEvent} if the phase advances.
     *
     * @param modelName     model identifier
     * @param distributorId distributor scope (null = global)
     * @return the phase after evaluation (may be the same as before if no change)
     */
    @Transactional
    public DataPhase evaluatePhase(String modelName, UUID distributorId) {
        AIDataPhase phase     = findOrCreate(modelName, distributorId);
        DataPhase   current   = phase.getCurrentPhase();
        TransitionThreshold t = ALL_THRESHOLDS.getOrDefault(
                modelName, TransitionThreshold.DEFAULT_THRESHOLD);

        DataPhase next       = computeNextPhase(phase, t);
        boolean   transitioned = (next != current);

        if (transitioned) {
            phase.setCurrentPhase(next);
            phase.setTransitionedAt(LocalDateTime.now());
        }
        phase.setLastEvaluatedAt(LocalDateTime.now());
        phaseRepository.save(phase);

        if (transitioned) {
            log.info("[DataPhase] {} {} → {} (real={}, ratio={})",
                    modelName, current, next,
                    phase.getRealDataCount(),
                    String.format("%.3f", phase.getRealDataRatio()));
            eventPublisher.publishEvent(new DataPhaseTransitionEvent(
                    this, modelName, distributorId,
                    current, next,
                    phase.getRealDataRatio(), phase.getRealDataCount()));
        }

        return phase.getCurrentPhase();
    }

    /**
     * Return the transition threshold for the given model name, or
     * {@link TransitionThreshold#DEFAULT_THRESHOLD} for unknown models.
     */
    public TransitionThreshold getThreshold(String modelName) {
        return ALL_THRESHOLDS.getOrDefault(modelName, TransitionThreshold.DEFAULT_THRESHOLD);
    }

    // ── Internal helpers ────────────────────────────────────────────────────

    /**
     * Compute the next phase without persisting. Phase advances monotonically:
     * SYNTHETIC → HYBRID if realCount ≥ hybridMinRealCount;
     * HYBRID → REAL if realCount ≥ realMinRealCount AND ratio ≥ realMinRatio.
     */
    private DataPhase computeNextPhase(AIDataPhase phase, TransitionThreshold t) {
        DataPhase current = phase.getCurrentPhase();
        int    real  = phase.getRealDataCount();
        double ratio = phase.getRealDataRatio();

        if (current == DataPhase.SYNTHETIC && real >= t.hybridMinRealCount()) {
            return DataPhase.HYBRID;
        }
        if (current == DataPhase.HYBRID
                && real >= t.realMinRealCount()
                && ratio >= t.realMinRatio()) {
            return DataPhase.REAL;
        }
        return current;
    }

    /**
     * Finds the phase record in the repository, or returns a transient default
     * (not persisted) if none exists. Used for read-only operations.
     */
    private AIDataPhase findOrBuildDefault(String modelName, UUID distributorId) {
        return phaseRepository.findByModelNameAndDistributorId(modelName, distributorId)
                .orElseGet(() -> AIDataPhase.builder()
                        .modelName(modelName)
                        .currentPhase(DataPhase.SYNTHETIC)
                        .realDataCount(0)
                        .syntheticDataCount(0)
                        .realDataRatio(0.0)
                        .build());
    }

    /**
     * Finds the phase record, or creates and persists a new SYNTHETIC record if none exists.
     * Used for write operations.
     */
    private AIDataPhase findOrCreate(String modelName, UUID distributorId) {
        return phaseRepository.findByModelNameAndDistributorId(modelName, distributorId)
                .orElseGet(() -> {
                    Distributor distributor = (distributorId != null)
                            ? distributorRepository.findById(distributorId).orElse(null)
                            : null;
                    AIDataPhase newPhase = AIDataPhase.builder()
                            .modelName(modelName)
                            .distributor(distributor)
                            .currentPhase(DataPhase.SYNTHETIC)
                            .realDataCount(0)
                            .syntheticDataCount(0)
                            .realDataRatio(0.0)
                            .build();
                    return phaseRepository.save(newPhase);
                });
    }
}
