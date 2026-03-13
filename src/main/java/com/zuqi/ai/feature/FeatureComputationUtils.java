package com.zuqi.ai.feature;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;

/**
 * Shared static computation utilities for feature engineering.
 *
 * Extracted from {@link com.zuqi.ai.feature.MerchantFeatureServiceImpl} and
 * {@link com.zuqi.ai.feature.InventoryFeatureServiceImpl} to guarantee identical
 * computation logic between real (JPA-backed) and synthetic (in-memory) feature paths.
 *
 * All methods are stateless pure functions — no Spring dependencies.
 */
public final class FeatureComputationUtils {

    private FeatureComputationUtils() {}

    // ── Mathematical helpers ───────────────────────────────────────────────

    /**
     * Compute the ordinary-least-squares linear regression slope (β₁) for
     * paired (x, y) arrays.
     *
     * <p>Returns {@code 0.0} when the denominator is zero, or when
     * {@code x.length < 2}.
     */
    public static double computeLinearRegressionSlope(double[] x, double[] y) {
        int n = x.length;
        if (n < 2) return 0.0;

        double sumX  = Arrays.stream(x).sum();
        double sumY  = Arrays.stream(y).sum();
        double sumXY = 0.0;
        double sumX2 = 0.0;

        for (int i = 0; i < n; i++) {
            sumXY += x[i] * y[i];
            sumX2 += x[i] * x[i];
        }

        double numerator   = (n * sumXY) - (sumX * sumY);
        double denominator = (n * sumX2) - (sumX * sumX);

        return denominator != 0 ? numerator / denominator : 0.0;
    }

    /**
     * Compute the population standard deviation of the given values.
     *
     * <p>Returns {@code 0.0} for arrays with fewer than 2 elements.
     */
    public static double computeStandardDeviation(double[] values) {
        if (values.length < 2) return 0.0;

        double mean     = Arrays.stream(values).average().orElse(0.0);
        double variance = Arrays.stream(values)
                .map(v -> Math.pow(v - mean, 2))
                .average()
                .orElse(0.0);
        return Math.sqrt(variance);
    }

    /**
     * Compute {@code (numerator / denominator) × 100.0}.
     *
     * <p>Returns {@code 0.0} when {@code denominator == 0}.
     */
    public static double computePercentage(int numerator, int denominator) {
        if (denominator == 0) return 0.0;
        return ((double) numerator / denominator) * 100.0;
    }

    // ── Trend helpers ──────────────────────────────────────────────────────

    /**
     * Determine inventory consumption trend by comparing the 7-day rate to the 30-day rate.
     *
     * <p>Logic mirrors {@link com.zuqi.ai.feature.InventoryFeatureServiceImpl#computeConsumptionTrend}.
     *
     * @return {@code "INCREASING"}, {@code "DECREASING"}, or {@code "STABLE"}
     */
    public static String computeConsumptionTrend(BigDecimal rate7d, BigDecimal rate30d) {
        if (rate7d.compareTo(BigDecimal.ZERO) == 0 && rate30d.compareTo(BigDecimal.ZERO) == 0) {
            return "STABLE";
        }

        BigDecimal threshold = BigDecimal.valueOf(0.20);
        BigDecimal diff = rate30d.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO :
                rate7d.subtract(rate30d).divide(rate30d, 4, RoundingMode.HALF_UP);

        if (diff.compareTo(threshold) > 0)          return "INCREASING";
        if (diff.compareTo(threshold.negate()) < 0) return "DECREASING";
        return "STABLE";
    }

    /**
     * Determine demand trend direction using a linear regression slope over a
     * series of quantity values.
     *
     * @param values    time-ordered quantity values (oldest first)
     * @param threshold absolute slope threshold; slopes above → INCREASING, below −threshold → DECREASING
     * @return {@code "INCREASING"}, {@code "DECREASING"}, or {@code "STABLE"}
     */
    public static String computeTrendDirection(List<Double> values, double threshold) {
        if (values.size() < 2) return "STABLE";

        double[] x = new double[values.size()];
        double[] y = new double[values.size()];
        for (int i = 0; i < values.size(); i++) {
            x[i] = i;
            y[i] = values.get(i);
        }

        double slope = computeLinearRegressionSlope(x, y);
        if (slope >= threshold)  return "INCREASING";
        if (slope <= -threshold) return "DECREASING";
        return "STABLE";
    }
}
