package com.zuqi.ai.feature;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Service for computing inventory features for AI models.
 *
 * Provides feature sets for:
 * 1. Shrinkage detection - identifying inventory discrepancies and anomalies
 * 2. Stockout prediction - predicting when products will run out of stock
 *
 * Supports both inference mode (current data) and training mode (historical data).
 *
 * Blueprint reference: plan.md Section 4.2 - InventoryFeatureService
 */
public interface InventoryFeatureService {

    /**
     * Computes inventory features for a warehouse-SKU combination.
     * Uses current data by default.
     *
     * @param warehouseId the warehouse to compute features for
     * @param productId the product (SKU) to compute features for
     * @return computed inventory features
     * @throws IllegalArgumentException if warehouse or product not found
     */
    InventoryFeatures computeFeatures(UUID warehouseId, UUID productId);

    /**
     * Computes inventory features for a warehouse-SKU combination as of a specific date (for training).
     * Only considers data that existed before the asOfDate.
     *
     * @param warehouseId the warehouse to compute features for
     * @param productId the product (SKU) to compute features for
     * @param asOfDate the point-in-time cutoff date
     * @return computed inventory features
     * @throws IllegalArgumentException if warehouse or product not found
     */
    InventoryFeatures computeFeatures(UUID warehouseId, UUID productId, LocalDateTime asOfDate);

    /**
     * Evicts cached inventory features for a specific warehouse-product combination.
     * Call this after stock data is updated.
     *
     * @param warehouseId the warehouse whose cache should be evicted
     * @param productId the product whose cache should be evicted
     */
    void evictCache(UUID warehouseId, UUID productId);

    /**
     * Evicts all cached inventory features for a warehouse.
     * Call this when warehouse data changes significantly.
     *
     * @param warehouseId the warehouse whose cache should be evicted
     */
    void evictWarehouseCache(UUID warehouseId);
}
