package com.zuqi.domain.ai;

/**
 * Classifies why a synthetic data generation run was triggered.
 *
 * <ul>
 *   <li>FULL_SEED   — initial bootstrap for a new distributor with no real data</li>
 *   <li>INCREMENTAL — top-up run to extend the synthetic history window</li>
 *   <li>RETRAIN     — triggered by a new model training cycle (blends with current real data)</li>
 * </ul>
 */
public enum SyntheticRunType {
    FULL_SEED,
    INCREMENTAL,
    RETRAIN
}
