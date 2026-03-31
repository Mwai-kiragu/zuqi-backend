package com.zuqi.ai.event.handler;

import com.zuqi.ai.event.DeliveryCompletedEvent;
import com.zuqi.ai.event.OrderCreatedEvent;
import com.zuqi.ai.prediction.PredictionAlertService;
import com.zuqi.ai.prediction.RepPerformancePredictor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Event handler that re-evaluates a sales rep's performance score whenever they
 * place an order or complete a delivery.
 *
 * <p>Both events carry a {@code salesRepId}; the handler skips events where no
 * rep is assigned (e.g. web-portal orders without a field rep).
 *
 * <p>Scoring uses the current calendar month as the evaluation window, matching
 * {@link RepPerformancePredictor#predict(UUID)}.  The result is evaluated by
 * {@link PredictionAlertService} which raises a {@code REP_UNDERPERFORMANCE}
 * alert when the score falls below the configured threshold (default 60).
 *
 * <p>Runs asynchronously so it never blocks the order or delivery transaction.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RepPerformanceEventHandler {

    private final RepPerformancePredictor repPerformancePredictor;
    private final PredictionAlertService  predictionAlertService;

    @Async
    @EventListener
    public void handleOrderCreated(OrderCreatedEvent event) {
        if (event.salesRepId() == null) {
            return;  // order placed directly (no rep assigned)
        }
        evaluateRep(event.salesRepId(), event.distributorId(), "order", event.orderId());
    }

    @Async
    @EventListener
    public void handleDeliveryCompleted(DeliveryCompletedEvent event) {
        if (event.salesRepId() == null) {
            return;  // delivery has no rep association
        }
        evaluateRep(event.salesRepId(), event.distributorId(), "delivery", event.deliveryId());
    }

    private void evaluateRep(UUID salesRepId, UUID distributorId, String triggerType, UUID triggerId) {
        log.debug("Rep performance re-evaluation: rep={} trigger={}:{}", salesRepId, triggerType, triggerId);
        try {
            RepPerformancePredictor.RepPerformanceResult result =
                    repPerformancePredictor.predict(salesRepId);

            predictionAlertService.evaluateRepPerformanceAndAlert(result, distributorId);

        } catch (Exception e) {
            log.warn("Rep performance prediction failed for rep={} on {}={}: {}",
                    salesRepId, triggerType, triggerId, e.getMessage());
        }
    }
}
