package com.zuqi.ai.anomaly;

import com.zuqi.ai.feature.InventoryFeatures;
import com.zuqi.ai.feature.PaymentFeatures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tribuo.Feature;
import org.tribuo.MutableDataset;
import org.tribuo.anomaly.Event;
import org.tribuo.impl.ArrayExample;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for AnomalyFeatureBuilder — no Spring context required.
 *
 * Verifies correct feature count, feature names, label assignment,
 * and encoding logic for inventory (8 features) and payment (10 features).
 */
class AnomalyFeatureBuilderTest {

    private AnomalyFeatureBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new AnomalyFeatureBuilder();
    }

    // ── Inventory examples ─────────────────────────────────────────────────

    @Test
    void inventoryExample_hasEightFeatures() {
        ArrayExample<Event> example = builder.buildInventoryExample(typicalInventory());
        assertThat(example.size()).isEqualTo(12);
    }

    @Test
    void inventoryExample_labelledExpected() {
        ArrayExample<Event> example = builder.buildInventoryExample(typicalInventory());
        assertThat(example.getOutput().getType()).isEqualTo(Event.EventType.EXPECTED);
    }

    @Test
    void anomalousInventoryExample_labelledAnomalous() {
        ArrayExample<Event> example = builder.buildAnomalousInventoryExample(typicalInventory());
        assertThat(example.getOutput().getType()).isEqualTo(Event.EventType.ANOMALOUS);
    }

    @Test
    void inventoryExample_containsAllEightFeatureNames() {
        ArrayExample<Event> example = builder.buildInventoryExample(typicalInventory());
        List<String> names = featureNames(example);

        assertThat(names).containsExactlyInAnyOrder(
                AnomalyFeatureBuilder.FEAT_DISCREPANCY_PCT,
                AnomalyFeatureBuilder.FEAT_DISCREPANCY_NORM,
                AnomalyFeatureBuilder.FEAT_MANUAL_ADJ_COUNT_7D,
                AnomalyFeatureBuilder.FEAT_UNIQUE_ADJUSTING_USERS,
                AnomalyFeatureBuilder.FEAT_ADJ_TIME_ENTROPY,
                AnomalyFeatureBuilder.FEAT_CONSUMPTION_RATE_7D,
                AnomalyFeatureBuilder.FEAT_CONSUMPTION_RATE_30D,
                AnomalyFeatureBuilder.FEAT_CONSUMPTION_TREND,
                AnomalyFeatureBuilder.FEAT_PENDING_RESERVED_PCT,
                AnomalyFeatureBuilder.FEAT_EXPECTED_INCOMING_PCT,
                AnomalyFeatureBuilder.FEAT_CURRENT_STOCK_NORM,
                AnomalyFeatureBuilder.FEAT_EXPECTED_STOCK_NORM
        );
    }

    @Test
    void inventoryExample_noNaNOrInfiniteValues() {
        ArrayExample<Event> example = builder.buildInventoryExample(typicalInventory());
        for (Feature f : example) {
            assertThat(Double.isNaN(f.getValue())).as("NaN in " + f.getName()).isFalse();
            assertThat(Double.isInfinite(f.getValue())).as("Infinite in " + f.getName()).isFalse();
        }
    }

    @Test
    void inventoryExample_allNullFieldsDefaultToZeroNotNaN() {
        InventoryFeatures empty = InventoryFeatures.builder()
                .warehouseId(UUID.randomUUID())
                .productId(UUID.randomUUID())
                .computedAt(LocalDateTime.now())
                .build();

        ArrayExample<Event> example = builder.buildInventoryExample(empty);
        assertThat(example.size()).isEqualTo(12);
        for (Feature f : example) {
            assertThat(Double.isNaN(f.getValue())).as("NaN in " + f.getName()).isFalse();
        }
    }

    @Test
    void inventoryExample_increasingTrendEncodedAs1() {
        double trend = featureValue(builder.buildInventoryExample(inventoryWithTrend("INCREASING")),
                AnomalyFeatureBuilder.FEAT_CONSUMPTION_TREND);
        assertThat(trend).isEqualTo(1.0);
    }

    @Test
    void inventoryExample_decreasingTrendEncodedAsNeg1() {
        double trend = featureValue(builder.buildInventoryExample(inventoryWithTrend("DECREASING")),
                AnomalyFeatureBuilder.FEAT_CONSUMPTION_TREND);
        assertThat(trend).isEqualTo(-1.0);
    }

    @Test
    void inventoryExample_stableTrendEncodedAs0() {
        double trend = featureValue(builder.buildInventoryExample(inventoryWithTrend("STABLE")),
                AnomalyFeatureBuilder.FEAT_CONSUMPTION_TREND);
        assertThat(trend).isEqualTo(0.0);
    }

    @Test
    void inventoryDataset_sizeMatchesInput() {
        MutableDataset<Event> dataset = builder.buildInventoryDataset(
                List.of(typicalInventory(), typicalInventory(), typicalInventory()));
        assertThat(dataset.size()).isEqualTo(3);
    }

    // ── Payment examples ───────────────────────────────────────────────────

    @Test
    void paymentExample_hasTenFeatures() {
        ArrayExample<Event> example = builder.buildPaymentExample(typicalPayment());
        assertThat(example.size()).isEqualTo(10);
    }

    @Test
    void paymentExample_labelledExpected() {
        ArrayExample<Event> example = builder.buildPaymentExample(typicalPayment());
        assertThat(example.getOutput().getType()).isEqualTo(Event.EventType.EXPECTED);
    }

    @Test
    void anomalousPaymentExample_labelledAnomalous() {
        ArrayExample<Event> example = builder.buildAnomalousPaymentExample(typicalPayment());
        assertThat(example.getOutput().getType()).isEqualTo(Event.EventType.ANOMALOUS);
    }

    @Test
    void paymentExample_mpesaEncodedAs1_cashAs0() {
        ArrayExample<Event> example = builder.buildPaymentExample(paymentWithMethod("MPESA"));
        assertThat(featureValue(example, AnomalyFeatureBuilder.FEAT_PAYMENT_MPESA)).isEqualTo(1.0);
        assertThat(featureValue(example, AnomalyFeatureBuilder.FEAT_PAYMENT_CASH)).isEqualTo(0.0);
    }

    @Test
    void paymentExample_cashEncodedAs1_mpesaAs0() {
        ArrayExample<Event> example = builder.buildPaymentExample(paymentWithMethod("CASH"));
        assertThat(featureValue(example, AnomalyFeatureBuilder.FEAT_PAYMENT_MPESA)).isEqualTo(0.0);
        assertThat(featureValue(example, AnomalyFeatureBuilder.FEAT_PAYMENT_CASH)).isEqualTo(1.0);
    }

    @Test
    void paymentExample_isPartialTrue_encodedAs1() {
        PaymentFeatures features = PaymentFeatures.builder()
                .paymentId(UUID.randomUUID()).merchantId(UUID.randomUUID())
                .computedAt(LocalDateTime.now())
                .daysToPay(3.0).daysToPayVsMerchantAvg(0.0).gapSinceLastPaymentDays(7)
                .amountVsInvoiceRatio(0.5).amountVsMerchantAvg(0.5)
                .hourOfDay(10).isPartial(true).isLate(false)
                .paymentMethodEncoded("MPESA")
                .build();

        assertThat(featureValue(builder.buildPaymentExample(features),
                AnomalyFeatureBuilder.FEAT_IS_PARTIAL)).isEqualTo(1.0);
    }

    @Test
    void paymentExample_isLateTrue_encodedAs1() {
        PaymentFeatures features = PaymentFeatures.builder()
                .paymentId(UUID.randomUUID()).merchantId(UUID.randomUUID())
                .computedAt(LocalDateTime.now())
                .daysToPay(35.0).daysToPayVsMerchantAvg(20.0).gapSinceLastPaymentDays(35)
                .amountVsInvoiceRatio(1.0).amountVsMerchantAvg(1.0)
                .hourOfDay(23).isPartial(false).isLate(true)
                .paymentMethodEncoded("CASH")
                .build();

        assertThat(featureValue(builder.buildPaymentExample(features),
                AnomalyFeatureBuilder.FEAT_IS_LATE)).isEqualTo(1.0);
    }

    @Test
    void paymentExample_noNaNOrInfiniteValues() {
        ArrayExample<Event> example = builder.buildPaymentExample(typicalPayment());
        for (Feature f : example) {
            assertThat(Double.isNaN(f.getValue())).as("NaN in " + f.getName()).isFalse();
            assertThat(Double.isInfinite(f.getValue())).as("Infinite in " + f.getName()).isFalse();
        }
    }

    @Test
    void paymentDataset_sizeMatchesInput() {
        MutableDataset<Event> dataset = builder.buildPaymentDataset(
                List.of(typicalPayment(), typicalPayment()));
        assertThat(dataset.size()).isEqualTo(2);
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private InventoryFeatures typicalInventory() {
        return InventoryFeatures.builder()
                .warehouseId(UUID.randomUUID()).productId(UUID.randomUUID())
                .computedAt(LocalDateTime.now())
                .currentStock(BigDecimal.valueOf(500))
                .expectedStock(BigDecimal.valueOf(520))
                .discrepancy(BigDecimal.valueOf(-20))
                .discrepancyPct(-3.85)
                .manualAdjustmentCount7d(2)
                .adjustingUserIds(List.of(UUID.randomUUID()))
                .consumptionRate7d(BigDecimal.valueOf(70))
                .consumptionRate30d(BigDecimal.valueOf(280))
                .consumptionTrend("STABLE")
                .pendingReservedQty(BigDecimal.valueOf(50))
                .expectedIncomingQty(BigDecimal.valueOf(100))
                .build();
    }

    private InventoryFeatures inventoryWithTrend(String trend) {
        return InventoryFeatures.builder()
                .warehouseId(UUID.randomUUID()).productId(UUID.randomUUID())
                .computedAt(LocalDateTime.now())
                .consumptionTrend(trend)
                .build();
    }

    private PaymentFeatures typicalPayment() {
        return paymentWithMethod("MPESA");
    }

    private PaymentFeatures paymentWithMethod(String method) {
        return PaymentFeatures.builder()
                .paymentId(UUID.randomUUID()).merchantId(UUID.randomUUID())
                .computedAt(LocalDateTime.now())
                .daysToPay(5.0).daysToPayVsMerchantAvg(1.0).gapSinceLastPaymentDays(14)
                .amountVsInvoiceRatio(1.0).amountVsMerchantAvg(1.05)
                .hourOfDay(10).isPartial(false).isLate(false)
                .paymentMethodEncoded(method)
                .build();
    }

    private List<String> featureNames(ArrayExample<Event> example) {
        List<String> names = new ArrayList<>();
        for (Feature f : example) names.add(f.getName());
        return names;
    }

    private double featureValue(ArrayExample<Event> example, String name) {
        for (Feature f : example) {
            if (f.getName().equals(name)) return f.getValue();
        }
        throw new AssertionError("Feature not found: " + name);
    }
}
