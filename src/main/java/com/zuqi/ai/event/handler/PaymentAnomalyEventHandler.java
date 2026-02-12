package com.zuqi.ai.event.handler;

import com.zuqi.ai.event.PaymentRecordedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Event handler for payment-related AI operations.
 *
 * Triggered by PaymentRecordedEvent to perform:
 * - Payment anomaly detection (Isolation Forest)
 * - Merchant financial distress classification
 * - Collection rate analysis
 *
 * Blueprint reference: plan.md Section 7.6 - Payment Anomaly Detection
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentAnomalyEventHandler {

    // TODO: Inject PaymentAnomalyDetectionService when implemented in Phase 4

    @Async
    @EventListener
    public void handlePaymentRecorded(PaymentRecordedEvent event) {
        log.info("Received PaymentRecordedEvent for payment {} (merchant: {}, amount: {})",
                event.paymentId(), event.merchantId(), event.amount());

        try {
            // TODO Phase 4: Implement payment anomaly detection
            // anomalyDetectionService.detectPaymentAnomaly(event);

            log.debug("Payment anomaly detection completed for payment {}", event.paymentId());
        } catch (Exception e) {
            log.error("Failed to process payment anomaly detection for payment {}", event.paymentId(), e);
            // Don't rethrow - event processing failures should not break the transaction
        }
    }
}
