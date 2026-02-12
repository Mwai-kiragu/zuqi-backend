package com.zuqi.ai.credit;

import com.zuqi.domain.merchant.Merchant;
import com.zuqi.repository.MerchantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Scheduled job for periodic credit re-evaluation.
 *
 * Runs monthly to refresh credit scores for all active merchants.
 * Rate-limited to avoid overwhelming the LLM.
 *
 * Blueprint reference: implementation_plan.md Phase 2 Task 2.7
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CreditScoringScheduler {

    private final CreditScoringOrchestrator creditScoringOrchestrator;
    private final MerchantRepository merchantRepository;

    private static final long RATE_LIMIT_DELAY_MS = 5000; // 5 seconds between evaluations

    /**
     * Monthly credit re-evaluation job.
     *
     * Runs on the 1st of every month at 2:00 AM.
     * Iterates all active merchants and triggers credit evaluation.
     */
    @Scheduled(cron = "0 0 2 1 * ?") // 2:00 AM on the 1st of every month
    public void performMonthlyReEvaluation() {
        log.info("Starting monthly credit re-evaluation job");

        try {
            List<Merchant> activeMerchants = merchantRepository.findAll().stream()
                    .filter(Merchant::isActive)
                    .toList();

            log.info("Found {} active merchants for re-evaluation", activeMerchants.size());

            int successCount = 0;
            int failureCount = 0;

            for (Merchant merchant : activeMerchants) {
                try {
                    log.debug("Re-evaluating merchant {} ({})", merchant.getId(), merchant.getBusinessName());
                    creditScoringOrchestrator.evaluateMerchant(merchant.getId());
                    successCount++;

                    // Rate limit: wait between evaluations to avoid overwhelming LLM
                    if (successCount % 10 == 0) {
                        log.info("Re-evaluated {} merchants so far...", successCount);
                    }

                    TimeUnit.MILLISECONDS.sleep(RATE_LIMIT_DELAY_MS);

                } catch (Exception e) {
                    log.error("Failed to re-evaluate merchant {} ({}): {}",
                            merchant.getId(), merchant.getBusinessName(), e.getMessage());
                    failureCount++;
                }
            }

            log.info("Monthly credit re-evaluation completed: {} succeeded, {} failed",
                    successCount, failureCount);

        } catch (Exception e) {
            log.error("Monthly credit re-evaluation job failed: {}", e.getMessage(), e);
        }
    }

    /**
     * On-demand re-evaluation for testing.
     *
     * This method is intentionally left uncommented for manual triggering during development.
     * In production, remove or secure this method.
     */
    // @Scheduled(cron = "0 0 3 * * ?") // Daily at 3:00 AM for testing
    public void performDailyReEvaluationForTesting() {
        log.info("Running daily re-evaluation (TEST MODE)");
        performMonthlyReEvaluation();
    }
}
