package com.zuqi.ai.credit;

import com.zuqi.ai.feature.MerchantFeatures;
import com.zuqi.ai.feature.MerchantFeatureService;
import com.zuqi.ai.training.CreditModelTrainingPipeline;
import com.zuqi.ai.training.SyntheticMerchantDataGenerator;
import com.zuqi.domain.credit.MerchantCreditOutcome;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Scheduled service for retraining credit models with real merchant outcomes.
 *
 * Execution Strategy:
 * - Runs weekly (every Sunday at 2 AM)
 * - Fetches new outcomes (DEFAULT / NO_DEFAULT) from MerchantOutcomeTracker
 * - Builds training examples from real data
 * - Blends with synthetic data (progressive replacement)
 * - Retrains classifier and regressor
 * - Promotes models if quality gates pass
 * - Marks outcomes as "used for training"
 *
 * Data Mix Evolution:
 * - Month 1: 100% synthetic, 0% real
 * - Month 2: 80% synthetic, 20% real
 * - Month 3: 60% synthetic, 40% real
 * - Month 6: 20% synthetic, 80% real
 * - Month 12+: 100% real (or latest 5000 examples)
 *
 * Blueprint: ML_IMPLEMENTATION_PLAN.md - Phase 3, Task 8
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
        prefix = "zuqi.ai.credit-scoring",
        name = "retraining-enabled",
        havingValue = "true",
        matchIfMissing = false
)
public class CreditModelRetrainingScheduler {

    private final MerchantOutcomeTracker outcomeTracker;
    private final MerchantFeatureService merchantFeatureService;
    private final CreditModelTrainingPipeline trainingPipeline;
    private final SyntheticMerchantDataGenerator syntheticDataGenerator;

    /**
     * Weekly retraining job.
     * Runs every Sunday at 2:00 AM (cron: 0 0 2 ? * SUN)
     */
    @Scheduled(cron = "${zuqi.ai.credit-scoring.retraining-cron:0 0 2 ? * SUN}")
    public void weeklyRetraining() {
        log.info("=" .repeat(80));
        log.info("WEEKLY CREDIT MODEL RETRAINING - STARTED");
        log.info("=" .repeat(80));

        long startTime = System.currentTimeMillis();

        try {
            // Step 1: Fetch new outcomes
            List<MerchantCreditOutcome> newOutcomes = outcomeTracker.getUnusedOutcomesForTraining();

            if (newOutcomes.isEmpty()) {
                log.info("No new outcomes to train on. Skipping retraining.");
                return;
            }

            log.info("Found {} new outcomes for retraining", newOutcomes.size());

            // Step 2: Build training examples from real outcomes
            List<com.zuqi.ai.training.SyntheticMerchant> realMerchants =
                    buildRealMerchantData(newOutcomes);

            log.info("Built {} merchant records from real outcomes", realMerchants.size());

            // Step 3: Determine synthetic data mix
            int syntheticCount = calculateSyntheticDataCount(realMerchants.size());

            log.info("Generating {} synthetic merchants for data augmentation", syntheticCount);

            // Step 4: Generate synthetic merchants
            List<com.zuqi.ai.training.SyntheticMerchant> syntheticMerchants =
                    syntheticDataGenerator.generateDataset(syntheticCount);

            // Step 5: Blend real + synthetic
            List<com.zuqi.ai.training.SyntheticMerchant> blendedData = new ArrayList<>();
            blendedData.addAll(realMerchants);
            blendedData.addAll(syntheticMerchants);

            log.info("Total training data: {} merchants ({} real, {} synthetic)",
                    blendedData.size(), realMerchants.size(), syntheticMerchants.size());

            // Step 6: Run training pipeline
            CreditModelTrainingPipeline.TrainingPipelineResult result =
                    trainingPipeline.runPipeline(blendedData);

            if (!result.success()) {
                log.error("Retraining FAILED: {}", result.errorMessage());
                return;
            }

            log.info("Retraining SUCCEEDED:");
            log.info("  - Classifier AUC-ROC: {:.3f}", result.classifierEvaluation().aucRoc());
            log.info("  - Regressor R²: {:.3f}", result.regressorEvaluation().r2());
            log.info("  - Classifier Model ID: {}", result.classifierModelId());
            log.info("  - Regressor Model ID: {}", result.regressorModelId());

            // Step 7: Mark outcomes as used
            List<UUID> outcomeIds = newOutcomes.stream()
                    .map(MerchantCreditOutcome::getId)
                    .toList();

            outcomeTracker.markOutcomesAsUsed(outcomeIds);

            log.info("Marked {} outcomes as used for training", outcomeIds.size());

            long durationSec = (System.currentTimeMillis() - startTime) / 1000;
            log.info("=" .repeat(80));
            log.info("WEEKLY RETRAINING COMPLETE - Duration: {}s", durationSec);
            log.info("=" .repeat(80));

        } catch (Exception e) {
            log.error("Retraining failed with exception: {}", e.getMessage(), e);
        }
    }

    /**
     * Build SyntheticMerchant records from real merchant outcomes.
     *
     * For each outcome:
     * 1. Fetch merchant's features (current or historical)
     * 2. Create SyntheticMerchant with actual outcome
     *
     * @param outcomes Real merchant outcomes
     * @return List of SyntheticMerchant records
     */
    private List<com.zuqi.ai.training.SyntheticMerchant> buildRealMerchantData(
            List<MerchantCreditOutcome> outcomes) {

        List<com.zuqi.ai.training.SyntheticMerchant> merchants = new ArrayList<>();

        for (MerchantCreditOutcome outcome : outcomes) {
            try {
                // Fetch merchant's current features
                // Note: In production, you'd want to fetch historical features from the time
                // of the credit application, but for now we use current features
                MerchantFeatures features = merchantFeatureService.computeFeatures(outcome.getMerchantId());

                // Map outcome to default boolean
                boolean didDefault = outcome.getOutcome().equals("DEFAULT");

                // Create SyntheticMerchant with real data
                com.zuqi.ai.training.SyntheticMerchant merchant =
                        new com.zuqi.ai.training.SyntheticMerchant(
                                features,
                                didDefault,
                                "REAL_DATA", // Archetype name for real merchants
                                didDefault ? 1.0 : 0.0 // Actual outcome probability
                        );

                merchants.add(merchant);

            } catch (Exception e) {
                log.warn("Failed to build merchant data for outcome {}: {}",
                        outcome.getId(), e.getMessage());
            }
        }

        return merchants;
    }

    /**
     * Infer credit limit for training when actual value is unknown.
     *
     * Strategy:
     * - If outcome is DEFAULT: Use low limit (100,000 KES)
     * - If outcome is NO_DEFAULT: Use medium limit (300,000 KES)
     *
     * In production, you'd fetch the actual approved limit from the credit application.
     */
    private BigDecimal inferCreditLimit(MerchantCreditOutcome outcome) {
        if (outcome.getCreditApplicationId() != null) {
            // TODO: Fetch actual credit limit from CreditApplication entity
            // return creditApplicationRepository.findById(outcome.getCreditApplicationId())
            //         .map(CreditApplication::getApprovedLimit)
            //         .orElse(BigDecimal.valueOf(200000));
        }

        // Fallback: Use outcome-based heuristic
        if ("DEFAULT".equals(outcome.getOutcome())) {
            return BigDecimal.valueOf(100000); // Low limit for defaults
        } else {
            return BigDecimal.valueOf(300000); // Medium limit for successes
        }
    }

    /**
     * Calculate how many synthetic examples to generate for data augmentation.
     *
     * Strategy (progressive replacement):
     * - If real examples < 100: Generate 5000 synthetic (bootstrap)
     * - If real examples 100-500: Generate 3x real examples
     * - If real examples 500-1000: Generate 2x real examples
     * - If real examples 1000-3000: Generate 1x real examples (50/50 mix)
     * - If real examples > 3000: Generate 0.5x real examples (67% real, 33% synthetic)
     * - If real examples > 5000: No synthetic (100% real)
     *
     * @param realCount Number of real examples
     * @return Number of synthetic examples to generate
     */
    private int calculateSyntheticDataCount(int realCount) {
        if (realCount < 100) {
            return 5000; // Bootstrap phase
        } else if (realCount < 500) {
            return realCount * 3; // 75% synthetic, 25% real
        } else if (realCount < 1000) {
            return realCount * 2; // 67% synthetic, 33% real
        } else if (realCount < 3000) {
            return realCount; // 50% synthetic, 50% real
        } else if (realCount < 5000) {
            return realCount / 2; // 33% synthetic, 67% real
        } else {
            return 0; // 100% real
        }
    }
}
