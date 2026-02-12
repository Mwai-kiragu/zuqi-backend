package com.zuqi.ai.feature;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Service for computing sales rep performance features for AI models.
 *
 * Provides feature sets for:
 * 1. Sales rep underperformance detection - identifying at-risk reps
 * 2. Performance prediction - forecasting future performance
 *
 * Features are computed for a specific time period (e.g., week, month, quarter).
 *
 * Blueprint reference: plan.md Section 4.2 - SalesRepFeatureService
 */
public interface SalesRepFeatureService {

    /**
     * Computes sales rep performance features for a specific time period.
     *
     * @param salesRepId the sales rep to compute features for
     * @param periodStart the start of the performance period (inclusive)
     * @param periodEnd the end of the performance period (inclusive)
     * @return computed sales rep features
     * @throws IllegalArgumentException if sales rep not found
     */
    SalesRepFeatures computeFeatures(UUID salesRepId, LocalDateTime periodStart, LocalDateTime periodEnd);

    /**
     * Evicts cached sales rep features for a specific sales rep and period.
     * Call this after sales rep data is updated.
     *
     * @param salesRepId the sales rep whose cache should be evicted
     * @param periodStart the start of the performance period
     * @param periodEnd the end of the performance period
     */
    void evictCache(UUID salesRepId, LocalDateTime periodStart, LocalDateTime periodEnd);

    /**
     * Evicts all cached sales rep features for a sales rep.
     * Call this when sales rep profile changes significantly.
     *
     * @param salesRepId the sales rep whose cache should be evicted
     */
    void evictRepCache(UUID salesRepId);
}
