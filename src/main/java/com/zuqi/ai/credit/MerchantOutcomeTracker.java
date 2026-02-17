package com.zuqi.ai.credit;

import com.zuqi.domain.credit.MerchantCreditOutcome;
import com.zuqi.repository.MerchantCreditOutcomeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service for tracking real merchant credit outcomes (defaults, successes).
 *
 * Records actual outcomes when:
 * - Merchant defaults on payment (30+ days overdue)
 * - Merchant completes credit term successfully
 * - Manual override by admin (fraud, business closure, etc.)
 *
 * This data is used for:
 * - Retraining ML models with real outcomes (not just synthetic)
 * - Calculating model accuracy (predicted vs actual default rate)
 * - Identifying which merchant segments have higher risk
 *
 * Blueprint: ML_IMPLEMENTATION_PLAN.md - Phase 3, Task 8 (Real Data Tracking)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MerchantOutcomeTracker {

    private final MerchantCreditOutcomeRepository outcomeRepository;

    /**
     * Record that a merchant defaulted on credit.
     *
     * Triggered when:
     * - Payment is 30+ days overdue
     * - Manual override by admin
     *
     * @param merchantId       Merchant who defaulted
     * @param creditApplicationId Original credit application (optional)
     * @param reason           Reason for default (e.g., "30+ days overdue", "fraud", "business closed")
     * @param recordedBy       User ID who recorded the outcome (admin or system)
     */
    @Transactional
    public void recordDefault(UUID merchantId, UUID creditApplicationId, String reason, UUID recordedBy) {
        log.warn("Recording DEFAULT for merchant {}: {}", merchantId, reason);

        MerchantCreditOutcome outcome = MerchantCreditOutcome.builder()
                .merchantId(merchantId)
                .creditApplicationId(creditApplicationId)
                .outcome("DEFAULT")
                .outcomeDate(LocalDateTime.now())
                .reason(reason)
                .recordedBy(recordedBy)
                .usedForTraining(false)  // Will be set to true when used in retraining
                .build();

        outcomeRepository.save(outcome);

        log.info("Saved DEFAULT outcome for merchant {} (ID: {})", merchantId, outcome.getId());
    }

    /**
     * Record that a merchant successfully completed their credit term.
     *
     * Triggered when:
     * - All payments made on time
     * - Credit term completed without issues
     *
     * @param merchantId       Merchant who succeeded
     * @param creditApplicationId Original credit application (optional)
     * @param reason           Reason for success (e.g., "All payments on time", "Early repayment")
     */
    @Transactional
    public void recordSuccess(UUID merchantId, UUID creditApplicationId, String reason) {
        log.info("Recording SUCCESS for merchant {}: {}", merchantId, reason);

        MerchantCreditOutcome outcome = MerchantCreditOutcome.builder()
                .merchantId(merchantId)
                .creditApplicationId(creditApplicationId)
                .outcome("NO_DEFAULT")
                .outcomeDate(LocalDateTime.now())
                .reason(reason)
                .recordedBy(null)  // System-generated
                .usedForTraining(false)
                .build();

        outcomeRepository.save(outcome);

        log.info("Saved NO_DEFAULT outcome for merchant {} (ID: {})", merchantId, outcome.getId());
    }

    /**
     * Get all outcomes that have NOT been used for training yet.
     *
     * Used by retraining scheduler to fetch new data for incremental training.
     *
     * @return List of outcomes ready for training
     */
    @Transactional(readOnly = true)
    public List<MerchantCreditOutcome> getUnusedOutcomesForTraining() {
        List<MerchantCreditOutcome> outcomes = outcomeRepository.findByUsedForTrainingFalse();

        log.info("Found {} unused outcomes for training", outcomes.size());
        return outcomes;
    }

    /**
     * Mark outcomes as used for training.
     *
     * Called after successful model retraining to avoid duplicate training data.
     *
     * @param outcomeIds List of outcome IDs that were used
     */
    @Transactional
    public void markOutcomesAsUsed(List<UUID> outcomeIds) {
        log.info("Marking {} outcomes as used for training", outcomeIds.size());

        List<MerchantCreditOutcome> outcomes = outcomeRepository.findAllById(outcomeIds);

        outcomes.forEach(outcome -> outcome.setUsedForTraining(true));
        outcomeRepository.saveAll(outcomes);

        log.info("Marked {} outcomes as used", outcomes.size());
    }

    /**
     * Get outcome statistics for monitoring.
     *
     * Returns counts of DEFAULT vs NO_DEFAULT outcomes.
     *
     * @return Outcome statistics record
     */
    @Transactional(readOnly = true)
    public OutcomeStatistics getStatistics() {
        long totalOutcomes = outcomeRepository.count();
        long defaultCount = outcomeRepository.countByOutcome("DEFAULT");
        long successCount = outcomeRepository.countByOutcome("NO_DEFAULT");
        long unusedCount = outcomeRepository.countByUsedForTrainingFalse();

        double defaultRate = totalOutcomes > 0 ? (double) defaultCount / totalOutcomes : 0.0;

        log.info("Outcome statistics: total={}, defaults={}, successes={}, unused={}, default_rate={:.2f}%",
                totalOutcomes, defaultCount, successCount, unusedCount, defaultRate * 100);

        return new OutcomeStatistics(
                totalOutcomes,
                defaultCount,
                successCount,
                unusedCount,
                defaultRate
        );
    }

    /**
     * Check if a merchant has any default history.
     *
     * @param merchantId Merchant to check
     * @return true if merchant has defaulted before
     */
    @Transactional(readOnly = true)
    public boolean hasDefaultHistory(UUID merchantId) {
        long defaultCount = outcomeRepository.countByMerchantIdAndOutcome(merchantId, "DEFAULT");
        return defaultCount > 0;
    }

    /**
     * Get all outcomes for a specific merchant.
     *
     * @param merchantId Merchant ID
     * @return List of outcomes (chronological order)
     */
    @Transactional(readOnly = true)
    public List<MerchantCreditOutcome> getMerchantHistory(UUID merchantId) {
        return outcomeRepository.findByMerchantIdOrderByOutcomeDateDesc(merchantId);
    }

    /**
     * Record class for outcome statistics.
     */
    public record OutcomeStatistics(
            long totalOutcomes,
            long defaultCount,
            long successCount,
            long unusedForTraining,
            double defaultRate
    ) {}
}
