package com.zuqi.ai.service;

import com.zuqi.ai.dto.AIModelListResponse;
import com.zuqi.ai.dto.AIModelPerformanceResponse;
import com.zuqi.ai.dto.AISystemHealthResponse;

/**
 * Service interface for AI system health monitoring.
 *
 * Provides:
 * - Overall system health status
 * - Model registry inspection
 * - Performance metrics retrieval
 *
 * Blueprint reference: implementation_plan.md Task 1.11
 */
public interface AIHealthService {

    /**
     * Gets comprehensive AI system health status.
     *
     * @return health status including model registry, feature services, LLM connectivity, and cache
     */
    AISystemHealthResponse getSystemHealth();

    /**
     * Lists all models in the registry with summary information.
     *
     * @return list of models with versions and status
     */
    AIModelListResponse getActiveModels();

    /**
     * Gets detailed performance metrics for a specific model.
     *
     * @param modelName the model name
     * @return performance metrics including accuracy, precision, recall, and history
     */
    AIModelPerformanceResponse getModelPerformance(String modelName);
}
