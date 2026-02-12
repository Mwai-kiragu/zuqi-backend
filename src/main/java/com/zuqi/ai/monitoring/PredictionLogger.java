package com.zuqi.ai.monitoring;

import com.zuqi.domain.ai.AIPrediction;
import com.zuqi.domain.ai.EntityType;

import java.util.Map;
import java.util.UUID;

/**
 * Prediction Logger Service - Audit logging for all AI predictions
 * Part of Phase 1: Foundation Infrastructure
 * Blueprint reference: plan.md - Prediction Audit Log (KCB Compliance)
 */
public interface PredictionLogger {

    /**
     * Log a prediction made by an AI model
     *
     * @param modelName Model name
     * @param modelVersion Model version
     * @param entityType Type of entity being predicted
     * @param entityId Entity ID
     * @param distributorId Distributor ID for multi-tenancy
     * @param predictionValue Prediction result as JSON
     * @param confidenceScore Model confidence (0-1)
     * @param inputFeaturesHash SHA-256 hash of input features for deduplication
     * @return Logged prediction entity
     */
    AIPrediction logPrediction(
            String modelName,
            Integer modelVersion,
            EntityType entityType,
            UUID entityId,
            UUID distributorId,
            Map<String, Object> predictionValue,
            Double confidenceScore,
            String inputFeaturesHash);

    /**
     * Log when a human overrides an AI prediction
     *
     * @param predictionId Original prediction ID
     * @param overrideValue Overridden value
     * @param overrideBy User who made the override
     * @param overrideReason Reason for override
     * @return Updated prediction entity
     */
    AIPrediction logOverride(
            UUID predictionId,
            Map<String, Object> overrideValue,
            String overrideBy,
            String overrideReason);

    /**
     * Get prediction history for an entity
     *
     * @param entityType Entity type
     * @param entityId Entity ID
     * @param limit Maximum number of predictions to return
     * @return List of predictions
     */
    java.util.List<AIPrediction> getPredictionHistory(
            EntityType entityType,
            UUID entityId,
            int limit);

    /**
     * Get the latest prediction for an entity
     *
     * @param entityType Entity type
     * @param entityId Entity ID
     * @return Latest prediction if exists
     */
    java.util.Optional<AIPrediction> getLatestPrediction(
            EntityType entityType,
            UUID entityId);

    /**
     * Calculate override rate for a model (for model quality monitoring)
     *
     * @param modelName Model name
     * @param modelVersion Model version
     * @return Override rate (0-1)
     */
    double calculateOverrideRate(String modelName, Integer modelVersion);
}
