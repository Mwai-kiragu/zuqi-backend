package com.zuqi.ai.model.tuning.dto;

import com.zuqi.ai.model.tuning.TuningResult;

import java.util.List;
import java.util.UUID;

/**
 * Polling response for GET /v1/ai/admin/tune/{jobId}/status.
 *
 * @param jobId        the tuning job UUID
 * @param distributorId distributor scope
 * @param status       RUNNING | COMPLETED | COMPLETED_WITH_ERRORS | FAILED
 * @param results      per-model tuning results (empty while RUNNING)
 * @param error        error summary (non-null on FAILED / COMPLETED_WITH_ERRORS)
 * @param startedAt    epoch millis when the job started
 * @param durationMs   elapsed ms (0 while RUNNING)
 */
public record TuningStatusResponse(
        UUID               jobId,
        UUID               distributorId,
        String             status,
        List<TuningResult> results,
        String             error,
        long               startedAt,
        long               durationMs) {}
