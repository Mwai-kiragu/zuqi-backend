package com.zuqi.ai.prediction;

import com.zuqi.ai.anomaly.AlertService;
import com.zuqi.domain.ai.AlertSeverity;
import com.zuqi.domain.ai.AlertType;
import com.zuqi.domain.ai.AnomalyAlert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PredictionAlertService — no Spring context required.
 *
 * Verifies threshold logic and severity assignment for both
 * stockout risk and rep underperformance alerts.
 */
@ExtendWith(MockitoExtension.class)
class PredictionAlertServiceTest {

    @Mock private AlertService alertService;

    private PredictionAlertService service;

    private static final UUID WAREHOUSE_ID   = UUID.randomUUID();
    private static final UUID PRODUCT_ID     = UUID.randomUUID();
    private static final UUID SALES_REP_ID   = UUID.randomUUID();
    private static final UUID DISTRIBUTOR_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new PredictionAlertService(alertService);
        // Inject @Value thresholds directly (avoids needing Spring context)
        ReflectionTestUtils.setField(service, "stockoutAlertThreshold", 0.5);
        ReflectionTestUtils.setField(service, "repPerformanceAlertThreshold", 60.0);

        lenient().when(alertService.createAlert(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(mock(AnomalyAlert.class));
    }

    // ── Stockout alert threshold ───────────────────────────────────────────

    @Test
    void stockout_belowThreshold_noAlert() {
        StockoutPredictor.StockoutResult result = stockoutResult(0.49);

        service.evaluateStockoutAndAlert(result, DISTRIBUTOR_ID);

        verify(alertService, never()).createAlert(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void stockout_atThreshold_alertRaised() {
        StockoutPredictor.StockoutResult result = stockoutResult(0.5);

        service.evaluateStockoutAndAlert(result, DISTRIBUTOR_ID);

        verify(alertService, times(1)).createAlert(
                eq(AlertType.STOCKOUT_RISK), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void stockout_aboveThreshold_alertRaised() {
        StockoutPredictor.StockoutResult result = stockoutResult(0.65);

        service.evaluateStockoutAndAlert(result, DISTRIBUTOR_ID);

        verify(alertService, times(1)).createAlert(
                eq(AlertType.STOCKOUT_RISK), any(), any(), any(), any(), any(), any(), any());
    }

    // ── Stockout severity ─────────────────────────────────────────────────

    @Test
    void stockout_probabilityBelow70_severityMedium() {
        StockoutPredictor.StockoutResult result = stockoutResult(0.65);

        service.evaluateStockoutAndAlert(result, DISTRIBUTOR_ID);

        ArgumentCaptor<AlertSeverity> captor = ArgumentCaptor.forClass(AlertSeverity.class);
        verify(alertService).createAlert(any(), captor.capture(), any(), any(), any(), any(), any(), any());
        assertThat(captor.getValue()).isEqualTo(AlertSeverity.MEDIUM);
    }

    @Test
    void stockout_probabilityAtOrAbove70_severityHigh() {
        StockoutPredictor.StockoutResult result = stockoutResult(0.70);

        service.evaluateStockoutAndAlert(result, DISTRIBUTOR_ID);

        ArgumentCaptor<AlertSeverity> captor = ArgumentCaptor.forClass(AlertSeverity.class);
        verify(alertService).createAlert(any(), captor.capture(), any(), any(), any(), any(), any(), any());
        assertThat(captor.getValue()).isEqualTo(AlertSeverity.HIGH);
    }

    @Test
    void stockout_alertUsesCorrectEntityTypeAndId() {
        StockoutPredictor.StockoutResult result = stockoutResult(0.8);

        service.evaluateStockoutAndAlert(result, DISTRIBUTOR_ID);

        verify(alertService).createAlert(
                eq(AlertType.STOCKOUT_RISK), any(),
                eq("PRODUCT"), eq(PRODUCT_ID),
                eq(DISTRIBUTOR_ID), any(), any(), any());
    }

    // ── Rep performance alert threshold ───────────────────────────────────

    @Test
    void repPerformance_aboveThreshold_noAlert() {
        RepPerformancePredictor.RepPerformanceResult result = repResult(60.0);

        service.evaluateRepPerformanceAndAlert(result, DISTRIBUTOR_ID);

        verify(alertService, never()).createAlert(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void repPerformance_belowThreshold_alertRaised() {
        RepPerformancePredictor.RepPerformanceResult result = repResult(59.9);

        service.evaluateRepPerformanceAndAlert(result, DISTRIBUTOR_ID);

        verify(alertService, times(1)).createAlert(
                eq(AlertType.REP_UNDERPERFORMANCE), any(), any(), any(), any(), any(), any(), any());
    }

    // ── Rep performance severity ──────────────────────────────────────────

    @Test
    void repPerformance_scoreBetween40And60_severityMedium() {
        RepPerformancePredictor.RepPerformanceResult result = repResult(50.0);

        service.evaluateRepPerformanceAndAlert(result, DISTRIBUTOR_ID);

        ArgumentCaptor<AlertSeverity> captor = ArgumentCaptor.forClass(AlertSeverity.class);
        verify(alertService).createAlert(any(), captor.capture(), any(), any(), any(), any(), any(), any());
        assertThat(captor.getValue()).isEqualTo(AlertSeverity.MEDIUM);
    }

    @Test
    void repPerformance_scoreBelow40_severityHigh() {
        RepPerformancePredictor.RepPerformanceResult result = repResult(39.9);

        service.evaluateRepPerformanceAndAlert(result, DISTRIBUTOR_ID);

        ArgumentCaptor<AlertSeverity> captor = ArgumentCaptor.forClass(AlertSeverity.class);
        verify(alertService).createAlert(any(), captor.capture(), any(), any(), any(), any(), any(), any());
        assertThat(captor.getValue()).isEqualTo(AlertSeverity.HIGH);
    }

    @Test
    void repPerformance_alertUsesCorrectEntityTypeAndId() {
        RepPerformancePredictor.RepPerformanceResult result = repResult(30.0);

        service.evaluateRepPerformanceAndAlert(result, DISTRIBUTOR_ID);

        verify(alertService).createAlert(
                eq(AlertType.REP_UNDERPERFORMANCE), any(),
                eq("SALES_REP"), eq(SALES_REP_ID),
                eq(DISTRIBUTOR_ID), any(), any(), any());
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private StockoutPredictor.StockoutResult stockoutResult(double probability) {
        return StockoutPredictor.StockoutResult.builder()
                .warehouseId(WAREHOUSE_ID)
                .productId(PRODUCT_ID)
                .stockoutProbability(probability)
                .prediction(probability >= 0.5 ? "STOCKOUT" : "NO_STOCKOUT")
                .daysOfStockRemaining(probability >= 0.5 ? 2.0 : 20.0)
                .modelVersion("stockout_predictor")
                .build();
    }

    private RepPerformancePredictor.RepPerformanceResult repResult(double score) {
        return RepPerformancePredictor.RepPerformanceResult.builder()
                .salesRepId(SALES_REP_ID)
                .performanceScore(score)
                .performanceTier(score >= 85 ? "EXCELLENT" : score >= 70 ? "GOOD"
                        : score >= 55 ? "AVERAGE" : score >= 40 ? "AT_RISK" : "CRITICAL")
                .modelVersion("rep_performance_predictor")
                .build();
    }
}
