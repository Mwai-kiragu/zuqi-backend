package com.zuqi.ai.event.handler;

import com.zuqi.ai.credit.CreditScoringOrchestrator;
import com.zuqi.ai.event.MerchantCreatedEvent;
import com.zuqi.ai.service.MerchantEmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Event handler for merchant-related AI operations.
 *
 * Triggered by MerchantCreatedEvent to perform:
 * - Credit risk evaluation (LLM-based initial scoring)
 * - Merchant embedding generation for RAG
 * - Onboarding workflow automation
 *
 * Blueprint reference: implementation_plan.md Phase 2 Task 2.7
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MerchantCreditEventHandler {

    private final CreditScoringOrchestrator creditScoringOrchestrator;
    private final MerchantEmbeddingService merchantEmbeddingService;

    @Async
    @EventListener
    public void handleMerchantCreated(MerchantCreatedEvent event) {
        log.info("Received MerchantCreatedEvent for merchant {} (name: {}, distributor: {})",
                event.merchantId(), event.businessName(), event.distributorId());

        try {
            // Step 1: Generate merchant embedding for RAG
            log.debug("Generating embedding for new merchant {}", event.merchantId());
            merchantEmbeddingService.embedMerchant(event.merchantId());

            // Step 2: Perform initial credit risk evaluation
            log.debug("Evaluating credit for new merchant {}", event.merchantId());
            creditScoringOrchestrator.evaluateMerchant(event.merchantId());

            log.info("Merchant AI processing completed for merchant {}", event.merchantId());
        } catch (Exception e) {
            log.error("Failed to process merchant AI operations for merchant {}: {}",
                    event.merchantId(), e.getMessage(), e);
            // Don't rethrow - event processing failures should not break the transaction
        }
    }
}
