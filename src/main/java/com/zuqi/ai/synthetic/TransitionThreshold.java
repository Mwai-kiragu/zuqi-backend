package com.zuqi.ai.synthetic;

/**
 * Threshold configuration governing when a model advances between data phases.
 *
 * <p>Phase advancement rules:
 * <ul>
 *   <li>SYNTHETIC → HYBRID: {@code realCount ≥ hybridMinRealCount}</li>
 *   <li>HYBRID → REAL: {@code realCount ≥ realMinRealCount AND realRatio ≥ realMinRatio}</li>
 * </ul>
 * Transitions are monotonic — a phase never goes backwards.
 *
 * <p>All 9 production thresholds are defined as constants in {@link DataPhaseTracker}.
 * A {@link #DEFAULT_THRESHOLD default} is returned for unrecognised model names.
 *
 * @param modelName          Human-readable model identifier this applies to
 * @param hybridMinRealCount Minimum real training examples to enter HYBRID
 * @param realMinRealCount   Minimum real training examples to enter REAL
 * @param realMinRatio       Minimum real-data fraction to enter REAL (alongside count)
 */
public record TransitionThreshold(
        String modelName,
        int    hybridMinRealCount,
        int    realMinRealCount,
        double realMinRatio
) {
    /** Default threshold used when a model name is not explicitly registered. */
    public static final TransitionThreshold DEFAULT_THRESHOLD =
            new TransitionThreshold("default", 100, 300, 0.80);
}
