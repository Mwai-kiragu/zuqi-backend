package com.zuqi.ai.synthetic;

import com.zuqi.ai.synthetic.profiles.MerchantArchetype;

import java.util.Map;
import java.util.UUID;

/**
 * Configuration for a synthetic data generation run.
 *
 * Passed to all generators so every part of a run uses the same parameters.
 * Stored as {@code config_snapshot} JSONB in {@code ai_synthetic_runs} for
 * full reproducibility.
 *
 * @param distributorId    Target distributor (null = global/test run)
 * @param merchantCount    Number of synthetic merchants to generate
 * @param historyMonths    Months of historical data to generate per merchant
 * @param randomSeed       Seed for all RNGs — same seed produces identical dataset
 * @param archetypeRatios  Fraction of merchants per archetype (must sum to 1.0)
 */
public record SyntheticDataConfig(
        UUID distributorId,
        int merchantCount,
        int historyMonths,
        long randomSeed,
        Map<MerchantArchetype, Double> archetypeRatios
) {

    /** Default archetype distribution matching the plan specification. */
    public static final Map<MerchantArchetype, Double> DEFAULT_ARCHETYPE_RATIOS = Map.of(
            MerchantArchetype.STEADY_GROWER,      0.35,
            MerchantArchetype.STABLE_PERFORMER,   0.25,
            MerchantArchetype.INCONSISTENT_BUYER, 0.20,
            MerchantArchetype.NEW_ENTRANT,         0.10,
            MerchantArchetype.DECLINING_RISK,      0.07,
            MerchantArchetype.DEFAULTER,           0.03
    );

    /** Convenience factory: 500 merchants, 12-month history, default archetypes. */
    public static SyntheticDataConfig defaultConfig(UUID distributorId, long seed) {
        return new SyntheticDataConfig(distributorId, 500, 12, seed, DEFAULT_ARCHETYPE_RATIOS);
    }

    public SyntheticDataConfig {
        if (merchantCount <= 0) throw new IllegalArgumentException("merchantCount must be > 0");
        if (historyMonths  <= 0) throw new IllegalArgumentException("historyMonths must be > 0");
        if (archetypeRatios == null || archetypeRatios.isEmpty()) {
            throw new IllegalArgumentException("archetypeRatios must not be empty");
        }
        double sum = archetypeRatios.values().stream().mapToDouble(Double::doubleValue).sum();
        if (Math.abs(sum - 1.0) > 0.001) {
            throw new IllegalArgumentException("archetypeRatios must sum to 1.0 (got " + sum + ")");
        }
    }
}
