package com.zuqi.ai.crm;

import com.zuqi.ai.event.OrderCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Listens for {@link OrderCreatedEvent} and triggers a churn prediction refresh
 * for the ordering customer — placing a new order is the strongest signal that
 * a customer has not churned.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CustomerOrderEventHandler {

    private final ChurnPredictor churnPredictor;

    @Async
    @EventListener
    public void onOrderCreated(OrderCreatedEvent event) {
        try {
            // merchantId in OrderCreatedEvent maps to customerId in CRM domain
            churnPredictor.predict(event.merchantId(), event.distributorId());
            log.debug("[CrmEventHandler] Churn updated for customer={} after order={}",
                    event.merchantId(), event.orderId());
        } catch (Exception e) {
            log.warn("[CrmEventHandler] Failed to update churn prediction after order={}: {}",
                    event.orderId(), e.getMessage());
        }
    }
}
