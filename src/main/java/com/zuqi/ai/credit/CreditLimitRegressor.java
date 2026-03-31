package com.zuqi.ai.credit;

import com.zuqi.ai.feature.MerchantFeatureService;
import com.zuqi.ai.feature.MerchantFeatures;
import com.zuqi.ai.model.ModelLoaderService;
import com.zuqi.ai.model.ModelPhaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.tribuo.Example;
import org.tribuo.Model;
import org.tribuo.Prediction;
import org.tribuo.regression.Regressor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * XGBoost regressor for credit limit prediction.
 *
 * Predicts optimal credit limit in KES for a merchant.
 * Trained on synthetic data initially, evolves to real data over time.
 *
 * Blueprint: ML_IMPLEMENTATION_PLAN.md Task 4
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CreditLimitRegressor {

    private final ModelLoaderService modelLoader;
    private final MerchantFeatureService merchantFeatureService;
    private final CreditMlFeatureBuilder featureBuilder;
    private final ModelPhaseService phaseService;

    private static final String MODEL_NAME = "credit_limit_regressor";
    private static final BigDecimal MIN_LIMIT = BigDecimal.valueOf(50_000);   // 50k KES
    private static final BigDecimal MAX_LIMIT = BigDecimal.valueOf(10_000_000); // 10M KES

    /**
     * Predict optimal credit limit for a merchant.
     *
     * @param merchantId Merchant to evaluate
     * @return Predicted credit limit in KES
     */
    public BigDecimal predictCreditLimit(UUID merchantId) {
        try {
            // 1. Load active model
            Model<Regressor> model = modelLoader.loadModel(MODEL_NAME);
            if (model == null) {
                log.warn("No active model found for {}, returning default limit", MODEL_NAME);
                return defaultCreditLimit();
            }

            // 2. Compute merchant features
            MerchantFeatures features = merchantFeatureService.computeFeatures(merchantId);

            // 3. Build ML feature vector (use dummy target for inference)
            Example<Regressor> example = featureBuilder.buildRegressionExample(
                    features,
                    BigDecimal.ZERO  // Target not used during inference
            );

            // 4. Predict
            Prediction<Regressor> prediction = model.predict(example);

            // 5. Extract predicted value
            double predictedLimit = prediction.getOutput().getValues()[0];

            // 6. Apply constraints
            BigDecimal creditLimit = BigDecimal.valueOf(predictedLimit)
                    .max(MIN_LIMIT)    // Floor at 50k
                    .min(MAX_LIMIT)    // Cap at 10M
                    .setScale(0, RoundingMode.HALF_UP);  // Round to nearest KES

            // Ensure it's a multiple of 10,000 for clean limits
            long limitLong = creditLimit.longValue();
            long rounded = (limitLong / 10_000) * 10_000;
            creditLimit = BigDecimal.valueOf(rounded);

            // Apply SYNTHETIC-phase confidence modifier — conservative limits when trained on generated data
            creditLimit = phaseService.applyModifier(creditLimit, MODEL_NAME);

            log.debug("ML credit limit prediction for merchant {}: {} KES (phase-adjusted)",
                    merchantId, creditLimit);

            return creditLimit;

        } catch (Exception e) {
            log.error("Credit limit prediction failed for merchant {}: {}",
                    merchantId, e.getMessage(), e);
            return defaultCreditLimit();
        } catch (Error e) {
            log.error("Fatal error in credit limit prediction for merchant {} (native library issue?): {}", merchantId, e.getMessage(), e);
            return defaultCreditLimit();
        }
    }

    /**
     * Default credit limit when model is unavailable or prediction fails.
     *
     * @return 100k KES (conservative default)
     */
    private BigDecimal defaultCreditLimit() {
        return BigDecimal.valueOf(100_000);
    }

    /**
     * Calculate ideal credit limit from merchant features (for training labels).
     *
     * Business logic for synthetic data generation:
     * - Base: 2x monthly order value
     * - Adjusted for default risk
     * - Floor: 50k KES, Cap: 1M KES
     *
     * @param features Merchant features
     * @param defaultProbability Default risk (0.0 - 1.0)
     * @return Ideal credit limit
     */
    public BigDecimal calculateIdealLimit(MerchantFeatures features,
                                           double defaultProbability) {
        // Estimate monthly order value
        BigDecimal monthlyOrderValue = features.avgOrderValue()
                .multiply(BigDecimal.valueOf(features.orderFrequencyPerWeek() * 4.33)); // 4.33 weeks/month

        // Base limit: 2x monthly volume
        BigDecimal baseLimit = monthlyOrderValue.multiply(BigDecimal.valueOf(2.0));

        // Risk adjustment: reduce limit for high-risk merchants
        // Low risk (5%) → 100% of base
        // Medium risk (20%) → 80% of base
        // High risk (50%) → 50% of base
        double riskMultiplier = 1.0 - (defaultProbability * 0.5);
        BigDecimal adjustedLimit = baseLimit.multiply(BigDecimal.valueOf(riskMultiplier));

        // Apply additional constraints
        // Higher utilization → lower limit increase
        if (features.currentUtilizationRatio() > 0.80) {
            adjustedLimit = adjustedLimit.multiply(BigDecimal.valueOf(0.9));
        }

        // Strong payment history → higher limit
        if (features.onTimePaymentPct() > 0.90) {
            adjustedLimit = adjustedLimit.multiply(BigDecimal.valueOf(1.1));
        }

        // Long tenure → higher limit
        if (features.relationshipTenureDays() > 365) {
            adjustedLimit = adjustedLimit.multiply(BigDecimal.valueOf(1.15));
        }

        // Floor and cap
        BigDecimal finalLimit = adjustedLimit
                .max(MIN_LIMIT)
                .min(MAX_LIMIT)
                .setScale(0, RoundingMode.HALF_UP);

        // Round to nearest 10k
        long limitLong = finalLimit.longValue();
        long rounded = ((limitLong + 5_000) / 10_000) * 10_000;

        return BigDecimal.valueOf(rounded);
    }
}
