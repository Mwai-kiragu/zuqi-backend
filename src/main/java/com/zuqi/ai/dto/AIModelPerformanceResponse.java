package com.zuqi.ai.dto;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Response DTO for AI model performance metrics.
 *
 * Blueprint reference: implementation_plan.md Task 1.11
 */
@Builder
public record AIModelPerformanceResponse(
        String modelName,
        String version,
        String status,
        PerformanceMetrics currentMetrics,
        List<PerformanceHistory> history,
        LocalDateTime retrievedAt
) {
    @Builder
    public record PerformanceMetrics(
            Double accuracy,
            Double precision,
            Double recall,
            Double f1Score,
            Double mae,
            Double rmse,
            Map<String, Object> customMetrics
    ) {}

    @Builder
    public record PerformanceHistory(
            LocalDateTime recordedAt,
            Double accuracy,
            Double precision,
            Double recall,
            Double f1Score
    ) {}
}
