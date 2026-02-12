package com.zuqi.ai.event.handler;

import com.zuqi.ai.event.MerchantCreatedEvent;
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
 * Blueprint reference: plan.md Section 7.1 - Credit Risk Scoring
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MerchantCreditEventHandler {

    // TODO: Inject CreditScoringService when implemented in Phase 2
    // TODO: Inject MerchantEmbeddingService when implemented in Phase 2

    @Async
    @EventListener
    public void handleMerchantCreated(MerchantCreatedEvent event) {
        log.info("Received MerchantCreatedEvent for merchant {} (name: {}, distributor: {})",
                event.merchantId(), event.businessName(), event.distributorId());

        try {
            // TODO Phase 2: Perform initial credit risk evaluation
            // creditScoringService.evaluateNewMerchant(event.merchantId());

            // TODO Phase 2: Generate merchant embedding for RAG
            // merchantEmbeddingService.generateEmbedding(event.merchantId());

            log.debug("Merchant AI processing completed for merchant {}", event.merchantId());
        } catch (Exception e) {
            log.error("Failed to process merchant AI operations for merchant {}", event.merchantId(), e);
            // Don't rethrow - event processing failures should not break the transaction
        }
    }
}
