package com.zuqi.ai.event.handler;

import com.zuqi.ai.anomaly.DataQualityDetector;
import com.zuqi.ai.event.OrderCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Event handler for order-related AI operations.
 *
 * Triggered by OrderCreatedEvent to run Tier-1 data quality validation.
 *
 * Blueprint reference: plan.md Section 6.3 - DataQualityDetector
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderDataQualityEventHandler {

    private final DataQualityDetector dataQualityDetector;

    @Async
    @EventListener
    public void handleOrderCreated(OrderCreatedEvent event) {
        log.debug("OrderCreatedEvent: order={} merchant={} items={} total={}",
                event.orderId(), event.merchantId(),
                event.items() != null ? event.items().size() : 0,
                event.totalAmount());

        try {
            dataQualityDetector.detect(event);
        } catch (Exception e) {
            log.error("Failed to process OrderCreatedEvent order={}: {}", event.orderId(), e.getMessage(), e);
        }
    }
}
