package com.zuqi.ai.model.tuning;

/**
 * Result of a holdout validation check performed after final model training.
 *
 * <p>A skipped result ({@link #skipped}) is returned when the holdout set is too small,
 * degenerate (single class, no anomalies), or when validation is not applicable
 * (K-Means). Skipped results are always treated as passing so the model is promoted.
 *
 * @param passed        whether the holdout metric cleared the threshold
 * @param metricName    name of the metric evaluated (e.g. "macro_f1", "nrmse", "anomaly_f1")
 * @param holdoutValue  actual metric value on the holdout set (NaN when skipped)
 * @param threshold     minimum/maximum threshold (NaN when skipped)
 */
public record ValidationResult(
        boolean passed,
        String  metricName,
        double  holdoutValue,
        double  threshold) {

    /**
     * Returns a passing result with NaN values, used when holdout validation
     * was skipped (too few examples, degenerate set, or unsupervised model).
     */
    public static ValidationResult skipped(String metricName) {
        return new ValidationResult(true, metricName, Double.NaN, Double.NaN);
    }

    /** True when this result represents a skipped (not evaluated) holdout check. */
    public boolean wasSkipped() {
        return Double.isNaN(holdoutValue);
    }
}
