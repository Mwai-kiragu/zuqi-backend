package com.zuqi.ai.model.tuning.dto;

import java.util.List;
import java.util.UUID;

/**
 * Response returned immediately by the POST /v1/ai/admin/tune/{distributorId} endpoint.
 *
 * @param jobId         UUID to poll for status
 * @param distributorId distributor scope
 * @param merchantCount number of synthetic merchants used for the bundle
 * @param modelsFilter  models being tuned (empty list = all 15 models)
 * @param statusUrl     poll URL for job status
 */
public record TuningJobResponse(
        UUID         jobId,
        UUID         distributorId,
        int          merchantCount,
        List<String> modelsFilter,
        String       statusUrl) {}
