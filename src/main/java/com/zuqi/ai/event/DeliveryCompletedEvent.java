package com.zuqi.ai.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Event published when a delivery is completed.
 *
 * Triggers AI operations:
 * - Route optimization learning (actual vs planned comparison)
 * - Sales rep performance tracking
 * - Delivery pattern analysis
 * - Stockout prediction updates
 *
 * Blueprint reference: plan.md Section 5 - Event-Driven AI Integration
 */
public record DeliveryCompletedEvent(
        UUID deliveryId,
        UUID orderId,
        UUID merchantId,
        UUID salesRepId,
        UUID distributorId,
        UUID routeId,
        LocalDateTime scheduledTime,
        LocalDateTime actualTime,
        Integer delayMinutes,
        boolean successful,
        String notes
) {
    public DeliveryCompletedEvent {
        if (deliveryId == null) {
            throw new IllegalArgumentException("deliveryId cannot be null");
        }
        if (orderId == null) {
            throw new IllegalArgumentException("orderId cannot be null");
        }
        if (merchantId == null) {
            throw new IllegalArgumentException("merchantId cannot be null");
        }
        if (distributorId == null) {
            throw new IllegalArgumentException("distributorId cannot be null");
        }
    }
}
