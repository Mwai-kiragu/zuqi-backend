package com.zuqi.ai.event.handler;

import com.zuqi.ai.anomaly.AlertService;
import com.zuqi.ai.anomaly.PaymentAnomalyDetector;
import com.zuqi.ai.event.PaymentRecordedEvent;
import com.zuqi.domain.ai.AlertSeverity;
import com.zuqi.domain.ai.AlertType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Event handler for payment-related AI operations.
 *
 * Triggered by PaymentRecordedEvent to run real-time payment anomaly detection.
 *
 * Blueprint reference: plan.md Section 6.3 - PaymentAnomalyDetector
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentAnomalyEventHandler {

    private final PaymentAnomalyDetector paymentAnomalyDetector;
    private final AlertService           alertService;

    @Async
    @EventListener
    public void handlePaymentRecorded(PaymentRecordedEvent event) {
        log.debug("PaymentRecordedEvent: payment={} merchant={} amount={}",
                event.paymentId(), event.merchantId(), event.amount());

        try {
            PaymentAnomalyDetector.PaymentAnomalyResult result =
                    paymentAnomalyDetector.detect(event.paymentId(), event.merchantId());

            if (result.isAnomaly()) {
                AlertSeverity severity = result.anomalyScore() > 0.7 ? AlertSeverity.CRITICAL
                        : result.anomalyScore() > 0.5 ? AlertSeverity.HIGH
                        : AlertSeverity.MEDIUM;

                Map<String, Object> context = Map.of(
                        "paymentId",     event.paymentId().toString(),
                        "merchantId",    event.merchantId().toString(),
                        "amount",        String.valueOf(event.amount()),
                        "paymentMethod", event.paymentMethod() != null ? event.paymentMethod() : "UNKNOWN",
                        "anomalyScore",  result.anomalyScore(),
                        "modelVersion",  result.modelVersion()
                );

                alertService.createAlert(
                        AlertType.PAYMENT_ANOMALY,
                        severity,
                        "PAYMENT",
                        event.paymentId(),
                        event.distributorId(),
                        result.anomalyScore(),
                        "Payment anomaly detected: merchant=" + event.merchantId()
                                + " amount=" + event.amount(),
                        context
                );

                log.info("Payment anomaly alert raised for payment={} merchant={} score={}",
                        event.paymentId(), event.merchantId(),
                        String.format("%.3f", result.anomalyScore()));
            }
        } catch (Exception e) {
            log.error("Failed to process PaymentRecordedEvent payment={}: {}",
                    event.paymentId(), e.getMessage(), e);
        }
    }
}
