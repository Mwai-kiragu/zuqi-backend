package com.zuqi.ai.dto;

import com.zuqi.domain.ai.ModelStatus;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO for listing AI models in the registry.
 *
 * Blueprint reference: implementation_plan.md Task 1.11
 */
@Builder
public record AIModelListResponse(
        List<ModelSummary> models,
        int totalCount,
        LocalDateTime retrievedAt
) {
    @Builder
    public record ModelSummary(
            java.util.UUID id,
            String modelName,
            String version,
            ModelStatus status,
            String modelType,
            Double accuracy,
            String primaryMetricName,   // e.g. "auc_roc", "rmse", "r2"
            Double primaryMetricValue,  // raw value from performance_metrics
            LocalDateTime trainedAt,
            LocalDateTime promotedAt
    ) {}
}
