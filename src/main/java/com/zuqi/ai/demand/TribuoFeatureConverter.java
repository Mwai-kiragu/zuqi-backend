package com.zuqi.ai.demand;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Shared utility for converting Java feature DTOs to Tribuo-compatible numeric values.
 *
 * Centralises common conversions (null-safe BigDecimal → double, categorical
 * encoding, feature name sanitisation) so they can be reused across demand
 * forecasting and future ML models (credit classifier, anomaly detection, etc.).
 *
 * Blueprint reference: implementation_plan.md Phase 3 Task 3.1
 */
@Component
public class TribuoFeatureConverter {

    /**
     * Safely converts a nullable {@link BigDecimal} to a {@code double}.
     *
     * @param value nullable BigDecimal
     * @return double value, or {@code 0.0} if value is null
     */
    public double safeDouble(BigDecimal value) {
        return value != null ? value.doubleValue() : 0.0;
    }

    /**
     * Encodes a trend direction string to a numeric value.
     *
     * <ul>
     *   <li>INCREASING →  1.0</li>
     *   <li>STABLE     →  0.0</li>
     *   <li>DECREASING → -1.0</li>
     * </ul>
     *
     * @param trendDirection trend direction string
     * @return numeric encoding
     */
    public double encodeTrend(String trendDirection) {
        if (trendDirection == null) return 0.0;
        return switch (trendDirection) {
            case "INCREASING" ->  1.0;
            case "DECREASING" -> -1.0;
            default            ->  0.0; // STABLE and unknown
        };
    }

    /**
     * Sanitises a category name for use as a Tribuo feature name.
     *
     * Converts to lower-case and replaces whitespace/hyphens with underscores,
     * removing any remaining non-alphanumeric characters.
     *
     * @param name raw category name
     * @return sanitised feature name
     */
    public String sanitizeFeatureName(String name) {
        return name.toLowerCase()
                .replace(" ", "_")
                .replace("-", "_")
                .replaceAll("[^a-z0-9_]", "");
    }
}
