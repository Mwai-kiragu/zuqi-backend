package com.zuqi.ai.feature;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Service for computing payment-related features for AI models.
 *
 * Provides two types of feature sets:
 * 1. Per-payment features - for payment anomaly detection (IsolationForest)
 * 2. Merchant payment trend features - for distress/default prediction (XGBoost)
 *
 * Supports both inference mode (current data) and training mode (historical data).
 *
 * Blueprint reference: plan.md Section 4.2 - PaymentFeatureService
 */
public interface PaymentFeatureService {

    /**
     * Computes features for a single payment (for anomaly detection).
     * Uses current data by default.
     *
     * @param paymentId the payment to compute features for
     * @return computed payment features
     * @throws IllegalArgumentException if payment not found
     */
    PaymentFeatures computePaymentFeatures(UUID paymentId);

    /**
     * Computes features for a single payment as of a specific date (for training).
     * Only considers data that existed before the asOfDate.
     *
     * @param paymentId the payment to compute features for
     * @param asOfDate the point-in-time cutoff date
     * @return computed payment features
     * @throws IllegalArgumentException if payment not found
     */
    PaymentFeatures computePaymentFeatures(UUID paymentId, LocalDateTime asOfDate);

    /**
     * Computes merchant-level payment trend features (for distress prediction).
     * Uses current data by default.
     *
     * @param merchantId the merchant to compute trend features for
     * @return computed merchant payment trend features
     * @throws IllegalArgumentException if merchant not found
     */
    MerchantPaymentTrendFeatures computeMerchantTrendFeatures(UUID merchantId);

    /**
     * Computes merchant-level payment trend features as of a specific date (for training).
     * Only considers data that existed before the asOfDate.
     *
     * @param merchantId the merchant to compute trend features for
     * @param asOfDate the point-in-time cutoff date
     * @return computed merchant payment trend features
     * @throws IllegalArgumentException if merchant not found
     */
    MerchantPaymentTrendFeatures computeMerchantTrendFeatures(UUID merchantId, LocalDateTime asOfDate);

    /**
     * Evicts cached payment features for a specific payment.
     * Call this after payment data is updated.
     *
     * @param paymentId the payment whose cache should be evicted
     */
    void evictPaymentCache(UUID paymentId);

    /**
     * Evicts cached merchant trend features for a specific merchant.
     * Call this after merchant payment data is updated.
     *
     * @param merchantId the merchant whose cache should be evicted
     */
    void evictMerchantTrendCache(UUID merchantId);
}
