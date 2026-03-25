package com.zuqi.ai.synthetic;

import com.zuqi.ai.feature.ExpiryFeatures;
import com.zuqi.ai.synthetic.generators.SyntheticExpiryBatchGenerator.SyntheticExpiryBatch;
import org.springframework.stereotype.Component;

/**
 * Builds ExpiryFeatures from a SyntheticExpiryBatch for use during training.
 *
 * Mirrors the logic of ExpiryFeatureServiceImpl so training and inference
 * use identical feature computation paths.
 *
 * Blueprint: phase2-plan.md Section 2.2
 */
@Component
public class SyntheticExpiryFeatureBuilder {

    /**
     * Compute ExpiryFeatures from a synthetic batch record.
     * distributorId/warehouseId/productId are null for training (not needed by feature builder).
     */
    public ExpiryFeatures buildFeatures(SyntheticExpiryBatch batch) {
        return new ExpiryFeatures(
                null,  // distributorId — not used by ExpiryRiskFeatureBuilder
                null,  // warehouseId
                null,  // productId
                batch.batchNumber(),
                batch.expiryDate(),
                batch.daysToExpiry(),
                batch.currentStockQty(),
                batch.avgDailySalesRate(),
                batch.projectedDaysToSell(),
                batch.similarSkuVelocity(),
                batch.warehouseTurnoverRate(),
                batch.priceSensitivityScore(),
                batch.batchAgeRatio()
        );
    }
}
