package com.zuqi.ai.synthetic.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Request body for {@code POST /v1/ai/admin/seed-synthetic/{distributorId}}.
 *
 * <p>All fields are optional — defaults are applied in
 * {@code SyntheticSeedController} before invoking the orchestrator:
 * <ul>
 *   <li>{@code merchantCount}  — default 500</li>
 *   <li>{@code historyMonths}  — default 12</li>
 *   <li>{@code randomSeed}     — default {@code System.currentTimeMillis()}</li>
 * </ul>
 *
 * @param merchantCount  Number of synthetic merchants (1–10,000)
 * @param historyMonths  Months of history per merchant (1–36)
 * @param randomSeed     RNG seed for reproducibility (null = random)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SyntheticSeedRequest(
        Integer merchantCount,
        Integer historyMonths,
        Long    randomSeed
) {
    /** Effective merchant count: caller value or default 500. */
    public int effectiveMerchantCount() {
        return merchantCount != null && merchantCount > 0 ? merchantCount : 500;
    }

    /** Effective history months: caller value or default 12. */
    public int effectiveHistoryMonths() {
        return historyMonths != null && historyMonths > 0 ? historyMonths : 12;
    }

    /** Effective seed: caller value or a time-based seed. */
    public long effectiveSeed() {
        return randomSeed != null ? randomSeed : System.currentTimeMillis();
    }
}
