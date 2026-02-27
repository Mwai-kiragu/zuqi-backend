package com.zuqi.ai.event.handler;

import com.zuqi.ai.anomaly.AlertService;
import com.zuqi.ai.anomaly.PaymentAnomalyDetector;
import com.zuqi.ai.anomaly.PaymentDistressClassifier;
import com.zuqi.ai.event.PaymentRecordedEvent;
import com.zuqi.domain.ai.AlertType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link PaymentAnomalyEventHandler}.
 *
 * Covers: anomaly alert, distress alert, dual-classification, and non-blocking
 * contract when either detector fails.
 */
@ExtendWith(MockitoExtension.class)
class PaymentAnomalyEventHandlerTest {

    @Mock private PaymentAnomalyDetector    paymentAnomalyDetector;
    @Mock private AlertService              alertService;
    @Mock private PaymentDistressClassifier paymentDistressClassifier;

    @InjectMocks
    private PaymentAnomalyEventHandler handler;

    // ── anomaly detected → PAYMENT_ANOMALY alert ──────────────────────────

    @Test
    void handlePaymentRecorded_whenAnomalyDetected_createsPaymentAnomalyAlert() {
        PaymentRecordedEvent event = buildEvent();

        when(paymentAnomalyDetector.detect(event.paymentId(), event.merchantId()))
                .thenReturn(anomalyResult(event, true, 0.65));

        when(paymentDistressClassifier.classify(event.merchantId()))
                .thenReturn(distressResult(event, false, 0.3));

        handler.handlePaymentRecorded(event);

        verify(alertService).createAlert(
                eq(AlertType.PAYMENT_ANOMALY),
                any(), eq("PAYMENT"), eq(event.paymentId()),
                eq(event.distributorId()), eq(0.65), anyString(), anyMap()
        );
    }

    @Test
    void handlePaymentRecorded_whenNoAnomaly_doesNotCreateAnomalyAlert() {
        PaymentRecordedEvent event = buildEvent();

        when(paymentAnomalyDetector.detect(any(), any()))
                .thenReturn(anomalyResult(event, false, 0.10));

        when(paymentDistressClassifier.classify(any()))
                .thenReturn(distressResult(event, false, 0.2));

        handler.handlePaymentRecorded(event);

        verify(alertService, never()).createAlert(
                eq(AlertType.PAYMENT_ANOMALY), any(), any(), any(), any(), any(), any(), any());
    }

    // ── distress detected → PAYMENT_DISTRESS alert ────────────────────────

    @Test
    void handlePaymentRecorded_whenDistressDetected_createsPaymentDistressAlert() {
        PaymentRecordedEvent event = buildEvent();

        when(paymentAnomalyDetector.detect(any(), any()))
                .thenReturn(anomalyResult(event, false, 0.1));

        when(paymentDistressClassifier.classify(event.merchantId()))
                .thenReturn(distressResult(event, true, 0.75));

        handler.handlePaymentRecorded(event);

        verify(alertService).createAlert(
                eq(AlertType.PAYMENT_DISTRESS),
                any(), eq("MERCHANT"), eq(event.merchantId()),
                eq(event.distributorId()), eq(0.75), anyString(), anyMap()
        );
    }

    @Test
    void handlePaymentRecorded_whenDistressBelowThreshold_doesNotCreateDistressAlert() {
        PaymentRecordedEvent event = buildEvent();

        when(paymentAnomalyDetector.detect(any(), any()))
                .thenReturn(anomalyResult(event, false, 0.1));

        when(paymentDistressClassifier.classify(any()))
                .thenReturn(distressResult(event, true, 0.4)); // below 0.5 threshold

        handler.handlePaymentRecorded(event);

        verify(alertService, never()).createAlert(
                eq(AlertType.PAYMENT_DISTRESS), any(), any(), any(), any(), any(), any(), any());
    }

    // ── non-blocking contract ─────────────────────────────────────────────

    @Test
    void handlePaymentRecorded_whenAnomalyDetectorThrows_doesNotPropagateException() {
        PaymentRecordedEvent event = buildEvent();

        when(paymentAnomalyDetector.detect(any(), any()))
                .thenThrow(new RuntimeException("Detector failed"));

        // distress classifier still runs (independent try-catch)
        when(paymentDistressClassifier.classify(any()))
                .thenReturn(distressResult(event, false, 0.2));

        handler.handlePaymentRecorded(event); // must not throw
    }

    @Test
    void handlePaymentRecorded_whenDistressClassifierThrows_doesNotPropagateException() {
        PaymentRecordedEvent event = buildEvent();

        when(paymentAnomalyDetector.detect(any(), any()))
                .thenReturn(anomalyResult(event, false, 0.1));

        when(paymentDistressClassifier.classify(any()))
                .thenThrow(new RuntimeException("Classifier failed"));

        handler.handlePaymentRecorded(event); // must not throw
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private PaymentRecordedEvent buildEvent() {
        return new PaymentRecordedEvent(
                UUID.randomUUID(),  // paymentId
                UUID.randomUUID(),  // orderId
                UUID.randomUUID(),  // merchantId
                UUID.randomUUID(),  // distributorId
                BigDecimal.valueOf(12_500),
                "MPESA",
                LocalDateTime.now(),
                "COMPLETED"
        );
    }

    private PaymentAnomalyDetector.PaymentAnomalyResult anomalyResult(
            PaymentRecordedEvent event, boolean isAnomaly, double score) {
        return PaymentAnomalyDetector.PaymentAnomalyResult.builder()
                .paymentId(event.paymentId())
                .merchantId(event.merchantId())
                .isAnomaly(isAnomaly)
                .anomalyScore(score)
                .modelVersion("v1")
                .build();
    }

    private PaymentDistressClassifier.DistressResult distressResult(
            PaymentRecordedEvent event, boolean isDistressed, double probability) {
        return new PaymentDistressClassifier.DistressResult(
                event.merchantId(), isDistressed, probability, "v1");
    }
}
