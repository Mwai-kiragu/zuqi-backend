package com.zuqi.ai.feature;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Computes merchant-level features for AI models.
 * Foundational service consumed by credit scoring, churn prediction, and merchant analysis.
 *
 * Blueprint reference: plan.md Section 4.2 - MerchantFeatureService
 */
public interface MerchantFeatureService {

    /**
     * Compute current features for a merchant (for real-time inference).
     *
     * @param merchantId Merchant UUID
     * @return Complete feature set with current values
     */
    MerchantFeatures computeFeatures(UUID merchantId);

    /**
     * Compute historical features as of a specific date (for model training).
     * Only considers data available before the specified date.
     *
     * @param merchantId Merchant UUID
     * @param asOfDate Point-in-time snapshot date
     * @return Feature set as it would have been computed at asOfDate
     */
    MerchantFeatures computeFeatures(UUID merchantId, LocalDateTime asOfDate);

    /**
     * Evict cached features for a merchant (call after significant data changes).
     *
     * @param merchantId Merchant UUID
     */
    void evictCache(UUID merchantId);
}
