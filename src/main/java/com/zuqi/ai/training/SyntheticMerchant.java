package com.zuqi.ai.training;

import com.zuqi.ai.feature.MerchantFeatures;
import lombok.Builder;

/**
 * Synthetic merchant profile with features and outcome label.
 *
 * Used for ML model training when real merchant outcome data
 * is not yet available.
 *
 * Blueprint: ML_IMPLEMENTATION_PLAN.md Task 1
 */
@Builder
public record SyntheticMerchant(
        MerchantFeatures features,
        boolean didDefault,
        String archetypeName,
        double defaultProbability  // From archetype, for debugging
) {
}
