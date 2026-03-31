package com.zuqi.ai.event.handler;

import com.zuqi.ai.demand.DemandForecaster;
import com.zuqi.ai.event.OrderCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Event handler that refreshes demand forecasts when a new order is placed.
 *
 * <p>Each order is a real demand signal: when a merchant buys product X, the demand
 * forecast for that merchant-product pair should reflect that new data point.
 * This handler runs {@link DemandForecaster} for every product line in the order
 * so that {@code ai_demand_forecasts} stays current between weekly batch retraining runs.
 *
 * <p>Runs asynchronously so it never blocks the order-creation transaction.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderDemandEventHandler {

    private final DemandForecaster demandForecaster;

    @Async
    @EventListener
    public void handleOrderCreated(OrderCreatedEvent event) {
        if (event.items() == null || event.items().isEmpty()) {
            return;
        }

        log.debug("OrderCreatedEvent → demand refresh: order={} merchant={} products={}",
                event.orderId(), event.merchantId(), event.items().size());

        for (OrderCreatedEvent.OrderItem item : event.items()) {
            try {
                demandForecaster.forecastDemand(event.merchantId(), item.productId());
            } catch (Exception e) {
                log.warn("Demand forecast failed for merchant={} product={}: {}",
                        event.merchantId(), item.productId(), e.getMessage());
                // Continue processing remaining products in the order
            }
        }
    }
}
