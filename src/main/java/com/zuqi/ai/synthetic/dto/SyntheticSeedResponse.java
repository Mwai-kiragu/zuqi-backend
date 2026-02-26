package com.zuqi.ai.synthetic.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

/**
 * Response body for {@code POST /v1/ai/admin/seed-synthetic/{distributorId}}.
 *
 * <p>The run is started asynchronously — the caller receives the {@code runId}
 * and polls {@code GET /v1/ai/admin/seed-synthetic/{runId}/status} for completion.
 *
 * @param runId          UUID of the newly created {@code ai_synthetic_runs} record
 * @param distributorId  Target distributor (null for global/test runs)
 * @param merchantCount  Effective merchant count used for this run
 * @param historyMonths  Effective history months used for this run
 * @param randomSeed     Effective RNG seed (saved for exact reproducibility)
 * @param statusUrl      Convenience URL for polling the run status
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SyntheticSeedResponse(
        UUID   runId,
        UUID   distributorId,
        int    merchantCount,
        int    historyMonths,
        long   randomSeed,
        String statusUrl
) {}
