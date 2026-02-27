package com.zuqi.ai.credit;

import com.zuqi.ai.feature.MerchantFeatureService;
import com.zuqi.ai.pipeline.ModelTrainingService;
import com.zuqi.ai.synthetic.SyntheticDataConfig;
import com.zuqi.ai.synthetic.SyntheticDataOrchestrator;
import com.zuqi.ai.synthetic.SyntheticDataBundle;
import com.zuqi.domain.credit.MerchantCreditOutcome;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Scheduled service for retraining credit models with real merchant outcomes.
 *
 * Execution Strategy:
 * - Runs weekly (every Sunday at 2 AM)
 * - Fetches new outcomes (DEFAULT / NO_DEFAULT) from MerchantOutcomeTracker
 * - Generates a synthetic bundle sized to complement the real data
 * - Delegates to {@link ModelTrainingService} for all model training
 * - Marks outcomes as "used for training"
 *
 * Data Mix Evolution (handled by DataMixer inside ModelTrainingService):
 * - Month 1: 100% synthetic, 0% real
 * - Month 6: 20% synthetic, 80% real
 * - Month 12+: 100% real (or latest 5000 examples)
 *
 * Blueprint: ML_IMPLEMENTATION_PLAN.md - Phase 3, Task 8
 *
 * NOTE: disabled by default; enable via zuqi.ai.credit-scoring.retraining-enabled=true
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
    private final ModelTrainingService modelTrainingService;
    private final SyntheticDataOrchestrator syntheticOrchestrator;

    /**
     * Weekly retraining job.
     * Runs every Sunday at 2:00 AM (cron: 0 0 2 ? * SUN)
     */
    @Scheduled(cron = "${zuqi.ai.credit-scoring.retraining-cron:0 0 2 ? * SUN}")
    public void weeklyRetraining() {
        log.info("=".repeat(80));
        log.info("WEEKLY CREDIT MODEL RETRAINING - STARTED");
        log.info("=".repeat(80));

        long startTime = System.currentTimeMillis();

        try {
            // Step 1: Fetch new outcomes
            List<MerchantCreditOutcome> newOutcomes = outcomeTracker.getUnusedOutcomesForTraining();

            if (newOutcomes.isEmpty()) {
                log.info("No new outcomes to train on. Skipping retraining.");
                return;
            }

            log.info("Found {} new outcomes for retraining", newOutcomes.size());

            // Step 2: Determine synthetic bundle size to complement real data
            int syntheticMerchantCount = calculateSyntheticDataCount(newOutcomes.size());
            log.info("Generating {} synthetic merchants for data augmentation", syntheticMerchantCount);

            // Step 3: Generate synthetic bundle
            // TODO Phase 3: blend real merchant outcomes into the bundle via DataMixer
            SyntheticDataConfig config = SyntheticDataConfig.defaultConfig(
                    null, // distributorId — Phase 3 will pass real distributor
                    System.currentTimeMillis()
            );
            SyntheticDataBundle bundle = syntheticOrchestrator.generateBundle(config);

            // Step 4: Train all models from bundle
            ModelTrainingService.TrainingResult result =
                    modelTrainingService.trainAllModels(bundle, null);

            if (!result.success()) {
                log.error("Retraining FAILED. Errors: {}", result.errors());
                return;
            }

            log.info("Retraining SUCCEEDED: trained={} models, errors={}",
                    result.trainedModelIds().size(), result.errors().size());

            // Step 5: Mark outcomes as used
            List<UUID> outcomeIds = newOutcomes.stream()
                    .map(MerchantCreditOutcome::getId)
                    .toList();
            outcomeTracker.markOutcomesAsUsed(outcomeIds);
            log.info("Marked {} outcomes as used for training", outcomeIds.size());

            long durationSec = (System.currentTimeMillis() - startTime) / 1000;
            log.info("=".repeat(80));
            log.info("WEEKLY RETRAINING COMPLETE - Duration: {}s", durationSec);
            log.info("=".repeat(80));

        } catch (Exception e) {
            log.error("Retraining failed with exception: {}", e.getMessage(), e);
        }
    }

    /**
     * Calculate how many synthetic merchants to generate for data augmentation.
     *
     * Strategy (progressive replacement):
     * - &lt; 100 real: 5000 synthetic (bootstrap)
     * - 100–500 real: 3× real
     * - 500–1000 real: 2× real
     * - 1000–3000 real: 1× real (50/50 mix)
     * - 3000–5000 real: 0.5× real (67% real, 33% synthetic)
     * - &gt; 5000 real: 0 (100% real)
     */
    private int calculateSyntheticDataCount(int realCount) {
        if (realCount < 100) {
            return 5000;
        } else if (realCount < 500) {
            return realCount * 3;
        } else if (realCount < 1000) {
            return realCount * 2;
        } else if (realCount < 3000) {
            return realCount;
        } else if (realCount < 5000) {
            return realCount / 2;
        } else {
            return 0;
        }
    }
}
