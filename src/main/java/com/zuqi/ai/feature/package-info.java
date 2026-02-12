/**
 * Feature Engineering Package
 *
 * <p>Contains feature engineering services that compute derived features from raw
 * operational data. This is the foundation layer for all AI modules.
 *
 * <p><b>Services:</b>
 * <ul>
 *   <li>MerchantFeatureService - Merchant-level features (order, payment, credit history)</li>
 *   <li>OrderFeatureService - Demand forecasting features (lag, temporal, context)</li>
 *   <li>PaymentFeatureService - Payment behavior features (anomaly detection)</li>
 *   <li>InventoryFeatureService - Inventory features (shrinkage, stockout prediction)</li>
 *   <li>SalesRepFeatureService - Sales rep performance features</li>
 *   <li>FeatureStore - Centralized feature access with caching</li>
 * </ul>
 *
 * <p><b>Key Principle:</b> Same feature computation logic for both training and inference
 * to ensure consistency between model development and production deployment.
 *
 * <p><b>Implementation Plan Reference:</b> Phase 1, Tasks 1.4-1.9
 * <p><b>Blueprint Reference:</b> plan.md Section 4 (Feature Engineering Layer)
 *
 * @since Phase 1
 */
package com.zuqi.ai.feature;
