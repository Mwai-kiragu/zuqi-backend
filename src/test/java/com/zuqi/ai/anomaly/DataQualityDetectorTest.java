package com.zuqi.ai.anomaly;

import com.zuqi.ai.event.OrderCreatedEvent;
import com.zuqi.domain.ai.AlertSeverity;
import com.zuqi.domain.ai.AlertType;
import com.zuqi.domain.ai.AnomalyAlert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DataQualityDetector — verifies all 6 data quality rules.
 */
@ExtendWith(MockitoExtension.class)
class DataQualityDetectorTest {

    @Mock
    private AlertService alertService;

    private DataQualityDetector detector;

    private static final UUID ORDER_ID      = UUID.randomUUID();
    private static final UUID MERCHANT_ID   = UUID.randomUUID();
    private static final UUID DISTRIBUTOR_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID    = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lenient().when(alertService.createAlert(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(mock(AnomalyAlert.class));
        detector = new DataQualityDetector(alertService);
    }

    // ── Rule 1: at least one item ──────────────────────────────────────────

    @Test
    void rule1_emptyItemList_raisesViolation() {
        OrderCreatedEvent event = orderWithItems(List.of());

        List<DataQualityDetector.DataQualityViolation> violations = detector.detect(event);

        assertThat(violations).anyMatch(v -> v.rule().equals("ORDER_MUST_HAVE_ITEMS"));
    }

    @Test
    void rule1_nullItemList_raisesViolation() {
        OrderCreatedEvent event = new OrderCreatedEvent(
                ORDER_ID, MERCHANT_ID, null, DISTRIBUTOR_ID,
                BigDecimal.valueOf(100), null, LocalDateTime.now(), "REGULAR");

        List<DataQualityDetector.DataQualityViolation> violations = detector.detect(event);

        assertThat(violations).anyMatch(v -> v.rule().equals("ORDER_MUST_HAVE_ITEMS"));
    }

    // ── Rule 2: positive unit price ───────────────────────────────────────

    @Test
    void rule2_nullUnitPrice_raisesViolation() {
        OrderCreatedEvent event = orderWithItems(List.of(
                new OrderCreatedEvent.OrderItem(PRODUCT_ID, 5, null, null)));

        List<DataQualityDetector.DataQualityViolation> violations = detector.detect(event);

        assertThat(violations).anyMatch(v -> v.rule().equals("POSITIVE_UNIT_PRICE_REQUIRED"));
    }

    @Test
    void rule2_zeroPunitPrice_raisesViolation() {
        OrderCreatedEvent event = orderWithItems(List.of(
                new OrderCreatedEvent.OrderItem(PRODUCT_ID, 5, BigDecimal.ZERO, null)));

        List<DataQualityDetector.DataQualityViolation> violations = detector.detect(event);

        assertThat(violations).anyMatch(v -> v.rule().equals("POSITIVE_UNIT_PRICE_REQUIRED"));
    }

    @Test
    void rule2_negativeUnitPrice_raisesViolation() {
        OrderCreatedEvent event = orderWithItems(List.of(
                new OrderCreatedEvent.OrderItem(PRODUCT_ID, 5, BigDecimal.valueOf(-10), null)));

        List<DataQualityDetector.DataQualityViolation> violations = detector.detect(event);

        assertThat(violations).anyMatch(v -> v.rule().equals("POSITIVE_UNIT_PRICE_REQUIRED"));
    }

    // ── Rule 4: quantity <= 10,000 ────────────────────────────────────────

    @Test
    void rule4_quantityExceedsThreshold_raisesViolation() {
        // OrderItem constructor validates quantity > 0, so use 10001
        OrderCreatedEvent event = orderWithItems(List.of(
                new OrderCreatedEvent.OrderItem(PRODUCT_ID, 10_001, BigDecimal.valueOf(100), null)));

        List<DataQualityDetector.DataQualityViolation> violations = detector.detect(event);

        assertThat(violations).anyMatch(v -> v.rule().equals("QUANTITY_EXCEEDS_THRESHOLD"));
    }

    @Test
    void rule4_quantityAtThreshold_noViolation() {
        OrderCreatedEvent event = orderWithItems(List.of(
                new OrderCreatedEvent.OrderItem(PRODUCT_ID, 10_000, BigDecimal.valueOf(100),
                        BigDecimal.valueOf(1_000_000))));

        List<DataQualityDetector.DataQualityViolation> violations = detector.detect(event);

        assertThat(violations).noneMatch(v -> v.rule().equals("QUANTITY_EXCEEDS_THRESHOLD"));
    }

    // ── Rule 6: total amount consistency ─────────────────────────────────

    @Test
    void rule6_totalAmountMatchesComputed_noViolation() {
        // 5 units × 100 = 500 total
        OrderCreatedEvent event = new OrderCreatedEvent(
                ORDER_ID, MERCHANT_ID, null, DISTRIBUTOR_ID,
                BigDecimal.valueOf(500),
                List.of(new OrderCreatedEvent.OrderItem(PRODUCT_ID, 5,
                        BigDecimal.valueOf(100), BigDecimal.valueOf(500))),
                LocalDateTime.now(), "REGULAR");

        List<DataQualityDetector.DataQualityViolation> violations = detector.detect(event);

        assertThat(violations).noneMatch(v -> v.rule().equals("TOTAL_AMOUNT_INCONSISTENT"));
    }

    @Test
    void rule6_totalAmountDiffersByMoreThan1Pct_raisesViolation() {
        // Computed: 5 × 100 = 500, but claimed 600 (20% off)
        OrderCreatedEvent event = new OrderCreatedEvent(
                ORDER_ID, MERCHANT_ID, null, DISTRIBUTOR_ID,
                BigDecimal.valueOf(600),
                List.of(new OrderCreatedEvent.OrderItem(PRODUCT_ID, 5,
                        BigDecimal.valueOf(100), BigDecimal.valueOf(500))),
                LocalDateTime.now(), "REGULAR");

        List<DataQualityDetector.DataQualityViolation> violations = detector.detect(event);

        assertThat(violations).anyMatch(v -> v.rule().equals("TOTAL_AMOUNT_INCONSISTENT"));
    }

    @Test
    void rule6_totalAmountWithin1Pct_noViolation() {
        // Computed: 500, claimed 504 (0.8% off — within tolerance)
        OrderCreatedEvent event = new OrderCreatedEvent(
                ORDER_ID, MERCHANT_ID, null, DISTRIBUTOR_ID,
                BigDecimal.valueOf(504),
                List.of(new OrderCreatedEvent.OrderItem(PRODUCT_ID, 5,
                        BigDecimal.valueOf(100), BigDecimal.valueOf(500))),
                LocalDateTime.now(), "REGULAR");

        List<DataQualityDetector.DataQualityViolation> violations = detector.detect(event);

        assertThat(violations).noneMatch(v -> v.rule().equals("TOTAL_AMOUNT_INCONSISTENT"));
    }

    // ── Clean order ───────────────────────────────────────────────────────

    @Test
    void cleanOrder_returnsNoViolationsAndNoAlert() {
        OrderCreatedEvent event = new OrderCreatedEvent(
                ORDER_ID, MERCHANT_ID, null, DISTRIBUTOR_ID,
                BigDecimal.valueOf(1000),
                List.of(new OrderCreatedEvent.OrderItem(PRODUCT_ID, 10,
                        BigDecimal.valueOf(100), BigDecimal.valueOf(1000))),
                LocalDateTime.now(), "REGULAR");

        List<DataQualityDetector.DataQualityViolation> violations = detector.detect(event);

        assertThat(violations).isEmpty();
        verify(alertService, never()).createAlert(any(), any(), any(), any(), any(), any(), any(), any());
    }

    // ── Alert creation ────────────────────────────────────────────────────

    @Test
    void violations_triggerSingleAlertPerOrder() {
        // Multiple violations on one order
        OrderCreatedEvent event = orderWithItems(List.of(
                new OrderCreatedEvent.OrderItem(PRODUCT_ID, 5, null, null),       // bad price
                new OrderCreatedEvent.OrderItem(PRODUCT_ID, 10_001, BigDecimal.valueOf(100), null))); // bad qty

        detector.detect(event);

        verify(alertService, times(1)).createAlert(
                eq(AlertType.DATA_QUALITY), any(), eq("ORDER"),
                eq(ORDER_ID), eq(DISTRIBUTOR_ID), any(), any(), any());
    }

    @Test
    void highSeverityViolation_alertUsesHighSeverity() {
        // Empty item list → HIGH severity
        OrderCreatedEvent event = orderWithItems(List.of());

        detector.detect(event);

        ArgumentCaptor<AlertSeverity> severityCaptor = ArgumentCaptor.forClass(AlertSeverity.class);
        verify(alertService).createAlert(any(), severityCaptor.capture(),
                any(), any(), any(), any(), any(), any());
        assertThat(severityCaptor.getValue()).isEqualTo(AlertSeverity.HIGH);
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private OrderCreatedEvent orderWithItems(List<OrderCreatedEvent.OrderItem> items) {
        BigDecimal total = items.stream()
                .filter(i -> i.unitPrice() != null && i.quantity() != null)
                .map(i -> i.unitPrice().multiply(BigDecimal.valueOf(i.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (total.compareTo(BigDecimal.ZERO) <= 0) total = BigDecimal.valueOf(100);

        return new OrderCreatedEvent(ORDER_ID, MERCHANT_ID, null, DISTRIBUTOR_ID,
                total, items, LocalDateTime.now(), "REGULAR");
    }
}
