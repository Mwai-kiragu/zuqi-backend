package com.zuqi.ai.event.handler;

import com.zuqi.ai.anomaly.AlertService;
import com.zuqi.ai.anomaly.ShrinkageDetector;
import com.zuqi.ai.event.StockAdjustedEvent;
import com.zuqi.domain.ai.AlertType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
 * Unit tests for {@link InventoryShrinkageEventHandler}.
 *
 * Covers: alert raised when anomaly detected, no alert when normal,
 * severity thresholds, and non-blocking contract when errors occur.
 */
@ExtendWith(MockitoExtension.class)
class InventoryShrinkageEventHandlerTest {

    @Mock private ShrinkageDetector shrinkageDetector;
    @Mock private AlertService      alertService;

    @InjectMocks
    private InventoryShrinkageEventHandler handler;

    // ── anomaly detected → alert raised ──────────────────────────────────

    @Test
    void handleStockAdjusted_whenAnomalyDetected_createsAlert() {
        StockAdjustedEvent event = buildEvent();

        ShrinkageDetector.ShrinkageResult result = ShrinkageDetector.ShrinkageResult.builder()
                .warehouseId(event.warehouseId())
                .productId(event.productId())
                .isAnomaly(true)
                .anomalyScore(0.65)
                .modelVersion("shrinkage_detector-v1")
                .build();

        when(shrinkageDetector.detect(event.warehouseId(), event.productId())).thenReturn(result);

        handler.handleStockAdjusted(event);

        verify(alertService).createAlert(
                eq(AlertType.SHRINKAGE),
                any(),
                eq("STOCK"),
                eq(event.stockId()),
                eq(event.distributorId()),
                eq(0.65),
                anyString(),
                anyMap()
        );
    }

    @Test
    void handleStockAdjusted_whenNotAnomaly_doesNotCreateAlert() {
        StockAdjustedEvent event = buildEvent();

        ShrinkageDetector.ShrinkageResult result = ShrinkageDetector.ShrinkageResult.builder()
                .warehouseId(event.warehouseId())
                .productId(event.productId())
                .isAnomaly(false)
                .anomalyScore(0.20)
                .modelVersion("fallback")
                .build();

        when(shrinkageDetector.detect(event.warehouseId(), event.productId())).thenReturn(result);

        handler.handleStockAdjusted(event);

        verifyNoInteractions(alertService);
    }

    // ── severity assignment ───────────────────────────────────────────────

    @Test
    void handleStockAdjusted_scoreAbove07_usesCriticalSeverity() {
        StockAdjustedEvent event = buildEvent();

        ShrinkageDetector.ShrinkageResult result = ShrinkageDetector.ShrinkageResult.builder()
                .warehouseId(event.warehouseId()).productId(event.productId())
                .isAnomaly(true).anomalyScore(0.75).modelVersion("v1").build();

        when(shrinkageDetector.detect(any(), any())).thenReturn(result);

        ArgumentCaptor<com.zuqi.domain.ai.AlertSeverity> severityCaptor =
                ArgumentCaptor.forClass(com.zuqi.domain.ai.AlertSeverity.class);
        when(alertService.createAlert(any(), severityCaptor.capture(), any(), any(), any(), any(), any(), any()))
                .thenReturn(null);

        handler.handleStockAdjusted(event);

        assertThat(severityCaptor.getValue())
                .isEqualTo(com.zuqi.domain.ai.AlertSeverity.CRITICAL);
    }

    @Test
    void handleStockAdjusted_scoreBetween05and07_usesHighSeverity() {
        StockAdjustedEvent event = buildEvent();

        ShrinkageDetector.ShrinkageResult result = ShrinkageDetector.ShrinkageResult.builder()
                .warehouseId(event.warehouseId()).productId(event.productId())
                .isAnomaly(true).anomalyScore(0.60).modelVersion("v1").build();

        when(shrinkageDetector.detect(any(), any())).thenReturn(result);

        ArgumentCaptor<com.zuqi.domain.ai.AlertSeverity> severityCaptor =
                ArgumentCaptor.forClass(com.zuqi.domain.ai.AlertSeverity.class);
        when(alertService.createAlert(any(), severityCaptor.capture(), any(), any(), any(), any(), any(), any()))
                .thenReturn(null);

        handler.handleStockAdjusted(event);

        assertThat(severityCaptor.getValue())
                .isEqualTo(com.zuqi.domain.ai.AlertSeverity.HIGH);
    }

    // ── non-blocking contract ─────────────────────────────────────────────

    @Test
    void handleStockAdjusted_whenDetectorThrows_doesNotPropagateException() {
        StockAdjustedEvent event = buildEvent();

        when(shrinkageDetector.detect(any(), any()))
                .thenThrow(new RuntimeException("Model unavailable"));

        // Should not throw
        handler.handleStockAdjusted(event);

        verifyNoInteractions(alertService);
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private StockAdjustedEvent buildEvent() {
        return new StockAdjustedEvent(
                UUID.randomUUID(),  // stockId
                UUID.randomUUID(),  // warehouseId
                UUID.randomUUID(),  // productId
                UUID.randomUUID(),  // distributorId
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(60),
                BigDecimal.valueOf(-40),
                "OUTBOUND",
                "Counted shortage",
                LocalDateTime.now()
        );
    }
}
