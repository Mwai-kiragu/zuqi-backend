package com.zuqi.ai.model;

import com.zuqi.domain.ai.AIModelRegistry;
import com.zuqi.domain.ai.DataPhase;
import com.zuqi.domain.ai.ModelStatus;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Model Registry Service - Manages ML model lifecycle
 * Part of Phase 1: Foundation Infrastructure
 * Blueprint reference: plan.md - Model Registry
 */
public interface ModelRegistry {

    /**
     * Register a new model version in the registry
     *
     * @param modelName Name of the model (e.g., "merchant-credit-scorer")
     * @param algorithm Algorithm used (e.g., "XGBoost", "Isolation Forest")
     * @param hyperparameters Model hyperparameters as JSON
     * @param createdBy User who created the model
     * @return Registered model entity
     */
    AIModelRegistry registerModel(
            String modelName,
            String algorithm,
            Map<String, Object> hyperparameters,
            String createdBy);

    /**
     * Update model with training results
     *
     * @param modelId Model ID
     * @param performanceMetrics Performance metrics (accuracy, precision, etc.)
     * @param modelBinary Serialized model bytes
     * @param featureColumns Feature column metadata
     */
    void updateModelAfterTraining(
            UUID modelId,
            Map<String, Object> performanceMetrics,
            byte[] modelBinary,
            Map<String, Object> featureColumns);

    /**
     * Promote model to ACTIVE status (production deployment)
     *
     * @param modelId Model ID to promote
     * @return Updated model
     */
    AIModelRegistry promoteToActive(UUID modelId);

    /**
     * Retire an active model
     *
     * @param modelId Model ID to retire
     * @return Updated model
     */
    AIModelRegistry retireModel(UUID modelId);

    /**
     * Get the currently active model for a given model name
     *
     * @param modelName Model name
     * @return Active model if exists
     */
    Optional<AIModelRegistry> getActiveModel(String modelName);

    /**
     * Get a specific model by name and version
     *
     * @param modelName Model name
     * @param version Model version
     * @return Model if exists
     */
    Optional<AIModelRegistry> getModel(String modelName, Integer version);

    /**
     * Get all versions of a model
     *
     * @param modelName Model name
     * @return List of all versions
     */
    List<AIModelRegistry> getAllVersions(String modelName);

    /**
     * Get all models with a specific status
     *
     * @param status Model status
     * @return List of models
     */
    List<AIModelRegistry> getModelsByStatus(ModelStatus status);

    /**
     * Stamp the data-phase provenance fields on a registered model.
     *
     * <p>Called after training to record how many synthetic vs. real examples were used
     * and what the data maturity phase was at training time.
     *
     * @param modelId          ID of the model to update
     * @param phase            data maturity phase (SYNTHETIC / HYBRID / REAL)
     * @param syntheticRecords number of synthetic training examples used
     * @param realRecords      number of real training examples used
     */
    void setDataPhaseMetadata(UUID modelId, DataPhase phase, int syntheticRecords, int realRecords);
}
