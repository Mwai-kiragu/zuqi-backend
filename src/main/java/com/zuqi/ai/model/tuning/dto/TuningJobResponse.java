package com.zuqi.ai.model.tuning.dto;

import java.util.UUID;

/**
 * Response returned immediately by the POST /v1/ai/admin/tune/{distributorId} endpoint.
 *
 * @param jobId       UUID to poll for status
 * @param distributorId distributor scope
 * @param merchantCount number of synthetic merchants used for the bundle
 * @param statusUrl   poll URL for job status
 */
public record TuningJobResponse(
        UUID   jobId,
        UUID   distributorId,
        int    merchantCount,
        String statusUrl) {}
