package com.zuqi.ai.model;

import com.zuqi.domain.ai.DataPhase;
import com.zuqi.ai.synthetic.DataPhaseTracker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Applies a confidence penalty to model output scores when the model is in the
 * SYNTHETIC data phase.
 *
 * <p>When a model has been trained exclusively on synthetic data it is inherently
 * less reliable than one trained on real observations.  Downstream callers should
 * therefore treat its scores with lower confidence.  This service centralises that
 * adjustment so every inference class applies the same multiplier consistently.
 *
 * <h3>Modifier values</h3>
 * <ul>
 *   <li>SYNTHETIC phase → multiply by {@link #SYNTHETIC_CONFIDENCE_MODIFIER} (0.6)</li>
 *   <li>HYBRID / REAL phase → no adjustment (multiplier = 1.0)</li>
 * </ul>
 *
 * <h3>Applies to</h3>
 * All 8 real-time inference classes:
 * {@code CreditClassifier}, {@code CreditLimitRegressor}, {@code ShrinkageDetector},
 * {@code PaymentAnomalyDetector}, {@code DemandForecaster}, {@code StockoutPredictor},
 * {@code PaymentDistressClassifier}, {@code RepPerformancePredictor}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ModelPhaseService {

    /** Confidence multiplier applied when a model is in the SYNTHETIC phase. */
    public static final double SYNTHETIC_CONFIDENCE_MODIFIER = 0.6;

    private final DataPhaseTracker phaseTracker;

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Apply the SYNTHETIC-phase confidence modifier to a {@code double} score.
     *
     * <p>Uses a {@code null} distributorId for a global (cross-distributor)
     * phase check, which is appropriate for bootstrap models trained on the
     * shared synthetic bundle.
     *
     * @param value     raw score / probability / confidence (any range)
     * @param modelName model identifier (e.g. {@code "credit_classifier"})
     * @return adjusted value, or the original value if the model is not in SYNTHETIC phase
     */
    public double applyModifier(double value, String modelName) {
        if (isSyntheticPhase(modelName)) {
            double adjusted = value * SYNTHETIC_CONFIDENCE_MODIFIER;
            log.trace("[ModelPhase] {} SYNTHETIC → adjusted score {:.4f} → {:.4f}",
                    modelName, value, adjusted);
            return adjusted;
        }
        return value;
    }

    /**
     * Apply the SYNTHETIC-phase confidence modifier to a {@link BigDecimal} value
     * (used for credit-limit predictions expressed in KES).
     *
     * @param value     raw predicted value
     * @param modelName model identifier
     * @return adjusted value rounded to the nearest integer KES, or the original
     *         value if the model is not in SYNTHETIC phase
     */
    public BigDecimal applyModifier(BigDecimal value, String modelName) {
        if (value == null) return null;
        if (isSyntheticPhase(modelName)) {
            BigDecimal adjusted = value.multiply(BigDecimal.valueOf(SYNTHETIC_CONFIDENCE_MODIFIER))
                    .setScale(0, RoundingMode.HALF_UP);
            log.trace("[ModelPhase] {} SYNTHETIC → adjusted BigDecimal {} → {}",
                    modelName, value, adjusted);
            return adjusted;
        }
        return value;
    }

    /**
     * Returns {@code true} when the given model is currently in the SYNTHETIC
     * data phase (i.e. trained entirely on generated data with no real observations).
     *
     * @param modelName model identifier
     * @return {@code true} if phase is {@link DataPhase#SYNTHETIC}
     */
    public boolean isSyntheticPhase(String modelName) {
        DataPhase phase = phaseTracker.getPhase(modelName, null);
        return phase == DataPhase.SYNTHETIC;
    }
}
