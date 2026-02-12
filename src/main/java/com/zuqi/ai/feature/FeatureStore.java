package com.zuqi.ai.feature;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Centralized feature store providing unified access to all feature services.
 *
 * Acts as a facade over individual feature services (Merchant, Order, Payment, Inventory, SalesRep),
 * providing a single entry point for feature retrieval with consistent caching and bulk operations.
 *
 * Blueprint reference: plan.md Section 4.2 - FeatureStore
 */
public interface FeatureStore {

    // ==================== Individual Feature Retrieval ====================

    /**
     * Gets merchant features (delegates to MerchantFeatureService).
     *
     * @param merchantId the merchant ID
     * @return merchant features
     */
    MerchantFeatures getMerchantFeatures(UUID merchantId);

    /**
     * Gets demand features for a merchant-product combination (delegates to OrderFeatureService).
     *
     * @param merchantId the merchant ID
     * @param productId the product ID
     * @return demand features
     */
    DemandFeatures getDemandFeatures(UUID merchantId, UUID productId);

    /**
     * Gets payment features for a specific payment (delegates to PaymentFeatureService).
     *
     * @param paymentId the payment ID
     * @return payment features
     */
    PaymentFeatures getPaymentFeatures(UUID paymentId);

    /**
     * Gets merchant payment trend features (delegates to PaymentFeatureService).
     *
     * @param merchantId the merchant ID
     * @return merchant payment trend features
     */
    MerchantPaymentTrendFeatures getMerchantPaymentTrendFeatures(UUID merchantId);

    /**
     * Gets inventory features for a warehouse-product combination (delegates to InventoryFeatureService).
     *
     * @param warehouseId the warehouse ID
     * @param productId the product ID
     * @return inventory features
     */
    InventoryFeatures getInventoryFeatures(UUID warehouseId, UUID productId);

    /**
     * Gets sales rep performance features for a period (delegates to SalesRepFeatureService).
     *
     * @param salesRepId the sales rep ID
     * @param periodStart the period start date
     * @param periodEnd the period end date
     * @return sales rep features
     */
    SalesRepFeatures getSalesRepFeatures(UUID salesRepId, LocalDateTime periodStart, LocalDateTime periodEnd);

    // ==================== Bulk Retrieval Methods ====================

    /**
     * Gets merchant features for all merchants under a distributor.
     * Useful for batch training and reporting operations.
     *
     * @param distributorId the distributor ID
     * @return list of merchant features
     */
    List<MerchantFeatures> getAllMerchantFeatures(UUID distributorId);

    /**
     * Gets demand features for all merchant-product combinations under a distributor.
     * Useful for batch demand forecasting operations.
     *
     * @param distributorId the distributor ID
     * @return list of demand features
     */
    List<DemandFeatures> getAllDemandFeatures(UUID distributorId);

    /**
     * Gets inventory features for all warehouse-product combinations under a distributor.
     * Useful for batch shrinkage detection and stockout prediction.
     *
     * @param distributorId the distributor ID
     * @return list of inventory features
     */
    List<InventoryFeatures> getAllInventoryFeatures(UUID distributorId);

    // ==================== Cache Management ====================

    /**
     * Invalidates all cached features for a specific merchant.
     * Call this when merchant data changes significantly.
     *
     * @param merchantId the merchant ID
     */
    void invalidateMerchantCache(UUID merchantId);

    /**
     * Invalidates all cached features for a specific warehouse.
     * Call this when warehouse data changes significantly.
     *
     * @param warehouseId the warehouse ID
     */
    void invalidateWarehouseCache(UUID warehouseId);

    /**
     * Invalidates all cached features for a specific sales rep.
     * Call this when sales rep data changes significantly.
     *
     * @param salesRepId the sales rep ID
     */
    void invalidateSalesRepCache(UUID salesRepId);

    /**
     * Refreshes (evicts and recomputes) all merchant features for a distributor.
     * Useful for nightly batch refresh operations.
     *
     * @param distributorId the distributor ID
     */
    void refreshAllMerchantFeatures(UUID distributorId);

    /**
     * Warms up the cache by pre-computing features for all active entities under a distributor.
     * Useful for system startup or after cache flush.
     *
     * @param distributorId the distributor ID
     */
    void warmUpCache(UUID distributorId);
}
