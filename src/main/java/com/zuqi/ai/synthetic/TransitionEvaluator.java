package com.zuqi.ai.synthetic;

import com.zuqi.ai.synthetic.dto.*;

import com.zuqi.domain.ai.DataPhase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * Evaluates whether a model has accumulated sufficient real data to move to
 * real-only training, checking both data volume and rare-event representation.
 *
 * <h3>Dual-gate check</h3>
 * A model passes {@link #meetsRealOnlyRequirements} when both gates are clear:
 * <ol>
 *   <li><b>Volume gate</b>: {@link DataPhaseTracker#getPhase} returns {@link DataPhase#REAL}
 *       (i.e. real count ≥ realMinRealCount AND ratio ≥ 80%).</li>
 *   <li><b>Rare-event gate</b>: the caller-supplied {@code rareEventCount} ≥ the per-model
 *       minimum defined in {@link #MIN_RARE_EVENTS}.</li>
 * </ol>
 *
 * <p>The zero-argument overload {@link #meetsRealOnlyRequirements(String, UUID)} checks only
 * the volume gate (useful when rare event counts are not tracked separately).
 *
 * <h3>Minimum rare events per model</h3>
 * <ul>
 *   <li>Credit classifier: 100 real defaults</li>
 *   <li>Credit limit regressor: 100 (needs full spread of credit histories)</li>
 *   <li>Payment distress classifier: 30 real distress events</li>
 *   <li>Shrinkage detector: 50 real shrinkage incidents</li>
 *   <li>Payment anomaly detector: 50 real anomalous payments</li>
 *   <li>Stockout predictor: 50 real stockout events</li>
 *   <li>All other models: 0 (no rare-event requirement)</li>
 * </ul>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TransitionEvaluator {

    /** Minimum rare-event examples needed in real training data before switching to REAL phase. */
    private static final Map<String, Integer> MIN_RARE_EVENTS = Map.of(
            DataPhaseTracker.MODEL_CREDIT_CLASSIFIER,           100,
            DataPhaseTracker.MODEL_CREDIT_LIMIT_REGRESSOR,      100,
            DataPhaseTracker.MODEL_PAYMENT_DISTRESS_CLASSIFIER,  30,
            DataPhaseTracker.MODEL_SHRINKAGE_DETECTOR,           50,
            DataPhaseTracker.MODEL_PAYMENT_ANOMALY_DETECTOR,     50,
            DataPhaseTracker.MODEL_STOCKOUT_PREDICTOR,           50
    );

    private final DataPhaseTracker phaseTracker;

    // ── Public API ──────────────────────────────────────────────────────────

    /**
     * Check if a model meets real-only requirements based on volume alone
     * (phase must be {@link DataPhase#REAL}).
     *
     * @param modelName     model identifier
     * @param distributorId distributor scope (null = global)
     * @return {@code true} if the model is in REAL phase
     */
    public boolean meetsRealOnlyRequirements(String modelName, UUID distributorId) {
        boolean meets = phaseTracker.getPhase(modelName, distributorId) == DataPhase.REAL;
        log.debug("[TransitionEval] {} meetsRealOnly(volume)={}", modelName, meets);
        return meets;
    }

    /**
     * Check if a model meets real-only requirements including rare event count.
     *
     * <p>Returns {@code true} only when:
     * <ol>
     *   <li>Phase is {@link DataPhase#REAL} (volume + ratio gates passed), AND</li>
     *   <li>{@code rareEventCount ≥ getMinRareEventCount(modelName)}</li>
     * </ol>
     *
     * @param modelName      model identifier
     * @param distributorId  distributor scope (null = global)
     * @param rareEventCount count of rare-class examples in the current real training data
     * @return {@code true} if both gates pass
     */
    public boolean meetsRealOnlyRequirements(String modelName, UUID distributorId,
                                             int rareEventCount) {
        if (!meetsRealOnlyRequirements(modelName, distributorId)) {
            return false;
        }
        int minRare   = getMinRareEventCount(modelName);
        boolean meets = rareEventCount >= minRare;
        log.debug("[TransitionEval] {} rareEventGate: have={} need={} meets={}",
                modelName, rareEventCount, minRare, meets);
        return meets;
    }

    /**
     * Return the minimum number of rare-class examples required in real training data
     * before a model qualifies for real-only training.
     *
     * @param modelName model identifier
     * @return minimum count, or 0 for models without a rare-event requirement
     */
    public int getMinRareEventCount(String modelName) {
        return MIN_RARE_EVENTS.getOrDefault(modelName, 0);
    }
}
