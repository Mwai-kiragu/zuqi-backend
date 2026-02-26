package com.zuqi.domain.ai;

/**
 * Tracks the maturity of training data for a model.
 *
 * <ul>
 *   <li>SYNTHETIC — model trained entirely on generated in-memory data (&lt;10% real)</li>
 *   <li>HYBRID    — model trained on a blend of synthetic and real data (10–80% real)</li>
 *   <li>REAL      — model trained predominantly on operational data (&gt;80% real)</li>
 * </ul>
 *
 * Transition thresholds are evaluated by {@code TransitionEvaluator} after each training run.
 */
public enum DataPhase {
    SYNTHETIC,
    HYBRID,
    REAL
}
