package com.zuqi.ai.model;

import org.tribuo.Model;

/**
 * Model Loader Service - Loads and caches trained ML models
 * Part of Phase 1: Foundation Infrastructure
 * Blueprint reference: plan.md - Model Loading with Cache
 */
public interface ModelLoader {

    /**
     * Load a trained Tribuo model from the registry
     *
     * @param modelName Name of the model
     * @return Loaded Tribuo model
     * @throws ModelNotFoundException if model not found or not ACTIVE
     * @throws ModelLoadException if deserialization fails
     */
    <T extends Model<?>> T loadModel(String modelName);

    /**
     * Load a specific version of a model
     *
     * @param modelName Model name
     * @param version Model version
     * @return Loaded Tribuo model
     * @throws ModelNotFoundException if model not found
     * @throws ModelLoadException if deserialization fails
     */
    <T extends Model<?>> T loadModel(String modelName, Integer version);

    /**
     * Evict model from cache (useful after model updates)
     *
     * @param modelName Model name to evict
     */
    void evictModel(String modelName);

    /**
     * Warm up cache by pre-loading all active models
     */
    void warmUpCache();

    /**
     * Get cache statistics
     *
     * @return Cache hit/miss stats
     */
    CacheStats getCacheStats();

    /**
     * Cache statistics holder
     */
    record CacheStats(long hits, long misses, long size, double hitRate) {}

    /**
     * Exception thrown when model is not found
     */
    class ModelNotFoundException extends RuntimeException {
        public ModelNotFoundException(String message) {
            super(message);
        }
    }

    /**
     * Exception thrown when model loading fails
     */
    class ModelLoadException extends RuntimeException {
        public ModelLoadException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
