package com.zuqi.ai.feature;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Service for computing demand forecasting features for merchant-SKU combinations.
 *
 * Provides feature sets for:
 * 1. Demand forecasting - predicting future order quantities
 * 2. AI-powered order suggestions - recommending optimal order quantities
 *
 * Supports both inference mode (current data) and training mode (historical data).
 *
 * Blueprint reference: plan.md Section 4.2 - OrderFeatureService
 */
public interface OrderFeatureService {

    /**
     * Computes demand features for a merchant-SKU combination.
     * Uses current data by default.
     *
     * @param merchantId the merchant to compute features for
     * @param productId the product (SKU) to compute features for
     * @return computed demand features
     * @throws IllegalArgumentException if merchant or product not found
     */
    DemandFeatures computeFeatures(UUID merchantId, UUID productId);

    /**
     * Computes demand features for a merchant-SKU combination as of a specific date (for training).
     * Only considers data that existed before the asOfDate.
     *
     * @param merchantId the merchant to compute features for
     * @param productId the product (SKU) to compute features for
     * @param asOfDate the point-in-time cutoff date
     * @return computed demand features
     * @throws IllegalArgumentException if merchant or product not found
     */
    DemandFeatures computeFeatures(UUID merchantId, UUID productId, LocalDateTime asOfDate);

    /**
     * Evicts cached demand features for a specific merchant-product combination.
     * Call this after order data is updated.
     *
     * @param merchantId the merchant whose cache should be evicted
     * @param productId the product whose cache should be evicted
     */
    void evictCache(UUID merchantId, UUID productId);

    /**
     * Evicts all cached demand features for a merchant.
     * Call this when merchant profile changes significantly.
     *
     * @param merchantId the merchant whose cache should be evicted
     */
    void evictMerchantCache(UUID merchantId);
}
