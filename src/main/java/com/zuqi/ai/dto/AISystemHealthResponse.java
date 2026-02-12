package com.zuqi.ai.dto;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Response DTO for AI system health status.
 *
 * Provides visibility into:
 * - Model registry status
 * - Feature services availability
 * - LLM connectivity (Ollama/Cloud)
 * - Cache health
 *
 * Blueprint reference: implementation_plan.md Task 1.11
 */
@Builder
public record AISystemHealthResponse(
        String status, // UP, DOWN, DEGRADED
        LocalDateTime timestamp,
        ModelRegistryHealth modelRegistry,
        FeatureServicesHealth featureServices,
        LLMConnectivityHealth llmConnectivity,
        CacheHealth cache,
        Map<String, Object> additionalInfo
) {
    @Builder
    public record ModelRegistryHealth(
            String status,
            int totalModels,
            int activeModels,
            String databaseConnection
    ) {}

    @Builder
    public record FeatureServicesHealth(
            String status,
            List<FeatureServiceStatus> services
    ) {}

    @Builder
    public record FeatureServiceStatus(
            String serviceName,
            String status,
            String cacheStatus
    ) {}

    @Builder
    public record LLMConnectivityHealth(
            String status,
            OllamaStatus ollama,
            CloudLLMStatus cloudLLM
    ) {}

    @Builder
    public record OllamaStatus(
            String status,
            String baseUrl,
            String model,
            String message
    ) {}

    @Builder
    public record CloudLLMStatus(
            String status,
            String provider,
            String message
    ) {}

    @Builder
    public record CacheHealth(
            String status,
            String provider, // REDIS, IN_MEMORY
            String connection
    ) {}
}
