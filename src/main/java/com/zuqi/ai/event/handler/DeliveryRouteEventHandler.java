package com.zuqi.ai.event.handler;

import com.zuqi.ai.event.DeliveryCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Event handler for delivery-related AI operations.
 *
 * Triggered by DeliveryCompletedEvent to perform:
 * - Route optimization learning (actual vs planned comparison)
 * - Sales rep performance tracking
 * - Delivery pattern analysis
 * - Stockout prediction updates
 *
 * Blueprint reference: plan.md Section 7.4 - Dynamic Route Optimization
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DeliveryRouteEventHandler {

    // TODO: Inject RouteOptimizationService when implemented in Phase 5
    // TODO: Inject SalesRepPerformanceService when implemented in Phase 4

    @Async
    @EventListener
    public void handleDeliveryCompleted(DeliveryCompletedEvent event) {
        log.info("Received DeliveryCompletedEvent for delivery {} (order: {}, merchant: {}, delay: {}min)",
                event.deliveryId(), event.orderId(), event.merchantId(), event.delayMinutes());

        try {
            // TODO Phase 5: Learn from actual delivery time vs planned
            // routeOptimizationService.learnFromDelivery(event);

            // TODO Phase 4: Update sales rep performance metrics
            // salesRepPerformanceService.trackDelivery(event.salesRepId(), event);

            log.debug("Delivery AI processing completed for delivery {}", event.deliveryId());
        } catch (Exception e) {
            log.error("Failed to process delivery AI operations for delivery {}", event.deliveryId(), e);
            // Don't rethrow - event processing failures should not break the transaction
        }
    }
}
