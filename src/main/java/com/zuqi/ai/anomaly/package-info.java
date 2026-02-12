/**
 * Anomaly Detection Package
 *
 * <p>ML-powered anomaly detection for inventory shrinkage, payment fraud,
 * and data quality issues.
 *
 * <p><b>Components:</b>
 * <ul>
 *   <li>ShrinkageDetector - Tribuo Isolation Forest for inventory</li>
 *   <li>PaymentAnomalyDetector - Tribuo Isolation Forest for payments</li>
 *   <li>PaymentDistressClassifier - Tribuo XGBoost for distress (Phase 2+)</li>
 *   <li>DataQualityDetector - Rules + Tribuo for data quality</li>
 *   <li>AnomalyFeatureBuilder - Builds anomaly-specific features</li>
 *   <li>AlertService - Alert generation, deduplication, delivery</li>
 * </ul>
 *
 * <p><b>Implementation Plan Reference:</b> Phase 4, Tasks 4.1-4.5
 * <p><b>Blueprint Reference:</b> plan.md Section 6.3 (Anomaly Detection Module)
 *
 * @since Phase 4
 */
package com.zuqi.ai.anomaly;
