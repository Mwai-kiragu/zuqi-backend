package com.zuqi.ai.synthetic.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Response body for {@code GET /v1/ai/admin/seed-synthetic/{runId}/status}.
 *
 * @param runId            UUID of the {@code ai_synthetic_runs} record
 * @param status           RUNNING | COMPLETED | FAILED
 * @param merchantCount    Number of merchants configured for this run
 * @param historyMonths    History window configured for this run
 * @param randomSeed       RNG seed for this run
 * @param startedAt        When the run was started
 * @param completedAt      When the run finished (null if still RUNNING)
 * @param durationMs       Wall-clock milliseconds (null if still RUNNING)
 * @param recordsGenerated Counts per entity type (null if still RUNNING or FAILED)
 * @param errorMessage     Failure reason (null unless FAILED)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SyntheticRunStatusResponse(
        UUID                 runId,
        String               status,
        Integer              merchantCount,
        Integer              historyMonths,
        Long                 randomSeed,
        LocalDateTime        startedAt,
        LocalDateTime        completedAt,
        Long                 durationMs,
        Map<String, Object>  recordsGenerated,
        String               errorMessage
) {}
