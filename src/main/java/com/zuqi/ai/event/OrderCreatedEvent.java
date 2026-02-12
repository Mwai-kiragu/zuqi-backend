package com.zuqi.ai.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Event published when an order is created.
 *
 * Triggers AI operations:
 * - Data quality validation
 * - Demand forecasting updates
 * - Sales rep performance tracking
 * - Order pattern analysis
 *
 * Blueprint reference: plan.md Section 5 - Event-Driven AI Integration
 */
public record OrderCreatedEvent(
        UUID orderId,
        UUID merchantId,
        UUID salesRepId,
        UUID distributorId,
        BigDecimal totalAmount,
        List<OrderItem> items,
        LocalDateTime createdAt,
        String orderType // REGULAR, CREDIT, SAMPLE
) {
    public OrderCreatedEvent {
        if (orderId == null) {
            throw new IllegalArgumentException("orderId cannot be null");
        }
        if (merchantId == null) {
            throw new IllegalArgumentException("merchantId cannot be null");
        }
        if (distributorId == null) {
            throw new IllegalArgumentException("distributorId cannot be null");
        }
        if (totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("totalAmount must be non-negative");
        }
    }

    /**
     * Simplified order item representation for event payload.
     */
    public record OrderItem(
            UUID productId,
            Integer quantity,
            BigDecimal unitPrice,
            BigDecimal subtotal
    ) {
        public OrderItem {
            if (productId == null) {
                throw new IllegalArgumentException("productId cannot be null");
            }
            if (quantity == null || quantity <= 0) {
                throw new IllegalArgumentException("quantity must be positive");
            }
        }
    }
}
