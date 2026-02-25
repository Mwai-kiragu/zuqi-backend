package com.zuqi.ai.event.handler;

import com.zuqi.ai.event.DeliveryCompletedEvent;
import com.zuqi.domain.ai.DeliveryRoute;
import com.zuqi.domain.ai.RouteStatus;
import com.zuqi.repository.DeliveryRouteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Event handler for delivery-related AI operations.
 *
 * On DeliveryCompletedEvent:
 * - Marks the corresponding stop in the route's stop_sequence as DELIVERED (or FAILED)
 * - Promotes route status to COMPLETED when all stops are done
 *
 * Blueprint reference: plan.md Section 7.4 - Dynamic Route Optimization
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DeliveryRouteEventHandler {

    private final DeliveryRouteRepository deliveryRouteRepository;

    @Async
    @EventListener
    @Transactional
    public void handleDeliveryCompleted(DeliveryCompletedEvent event) {
        log.info("DeliveryCompletedEvent: delivery={} order={} merchant={} route={} success={}",
                event.deliveryId(), event.orderId(), event.merchantId(),
                event.routeId(), event.successful());

        UUID routeId = event.routeId();
        if (routeId == null) {
            log.debug("No routeId on event for delivery={}, skipping route update", event.deliveryId());
            return;
        }

        try {
            Optional<DeliveryRoute> optional = deliveryRouteRepository.findById(routeId);
            if (optional.isEmpty()) {
                log.warn("Route {} not found for delivery completed event", event.routeId());
                return;
            }

            DeliveryRoute route = optional.get();
            String stopStatus = event.successful() ? "DELIVERED" : "FAILED";
            String orderId    = event.orderId().toString();

            // Mark the stop that contains this orderId
            List<Map<String, Object>> sequence = route.getStopSequence();
            boolean updated = false;
            for (Map<String, Object> stop : sequence) {
                @SuppressWarnings("unchecked")
                List<String> orderIds = (List<String>) stop.get("orderIds");
                if (orderIds != null && orderIds.contains(orderId)) {
                    stop.put("status", stopStatus);
                    updated = true;
                    log.debug("Marked stop for merchant={} as {} in route={}",
                            stop.get("merchantId"), stopStatus, event.routeId());
                    break;
                }
            }

            if (!updated) {
                log.warn("Order {} not found in stop sequence of route {}", orderId, event.routeId());
                return;
            }

            // Check if all stops are now terminal (DELIVERED or FAILED)
            boolean allDone = sequence.stream()
                    .allMatch(s -> "DELIVERED".equals(s.get("status")) || "FAILED".equals(s.get("status")));
            if (allDone) {
                route.setStatus(RouteStatus.COMPLETED);
                log.info("Route {} completed — all stops are terminal", event.routeId());
            }

            route.setStopSequence(sequence);
            deliveryRouteRepository.save(route);

        } catch (Exception e) {
            log.error("Failed to update route {} for delivery {}: {}",
                    event.routeId(), event.deliveryId(), e.getMessage(), e);
            // Don't rethrow — event processing failures must not break the caller
        }
    }
}
