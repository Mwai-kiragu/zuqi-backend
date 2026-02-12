package com.zuqi.ai.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Event published when a payment is recorded in the system.
 *
 * Triggers AI operations:
 * - Payment anomaly detection (Isolation Forest)
 * - Merchant distress classification
 * - Collection rate analysis
 *
 * Blueprint reference: plan.md Section 5 - Event-Driven AI Integration
 */
public record PaymentRecordedEvent(
        UUID paymentId,
        UUID orderId,
        UUID merchantId,
        UUID distributorId,
        BigDecimal amount,
        String paymentMethod,
        LocalDateTime recordedAt,
        String status
) {
    public PaymentRecordedEvent {
        if (paymentId == null) {
            throw new IllegalArgumentException("paymentId cannot be null");
        }
        if (merchantId == null) {
            throw new IllegalArgumentException("merchantId cannot be null");
        }
        if (distributorId == null) {
            throw new IllegalArgumentException("distributorId cannot be null");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("amount must be non-negative");
        }
    }
}
