package com.zuqi.ai.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Event published when a new merchant is registered.
 *
 * Triggers AI operations:
 * - Credit risk evaluation (LLM-based initial scoring)
 * - Merchant embedding generation for RAG
 * - Onboarding workflow automation
 *
 * Blueprint reference: plan.md Section 5 - Event-Driven AI Integration
 */
public record MerchantCreatedEvent(
        UUID merchantId,
        UUID distributorId,
        String businessName,
        String phone,
        String location,
        Long categoryId,
        UUID salesRepId,
        LocalDateTime createdAt
) {
    public MerchantCreatedEvent {
        if (merchantId == null) {
            throw new IllegalArgumentException("merchantId cannot be null");
        }
        if (distributorId == null) {
            throw new IllegalArgumentException("distributorId cannot be null");
        }
        if (businessName == null || businessName.isBlank()) {
            throw new IllegalArgumentException("businessName cannot be blank");
        }
    }
}
