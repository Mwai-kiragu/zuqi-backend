package com.zuqi.ai.synthetic;

import com.zuqi.ai.synthetic.dto.*;

import com.zuqi.ai.feature.MerchantPaymentTrendFeatures;
import com.zuqi.ai.feature.PaymentFeatures;
import com.zuqi.ai.synthetic.profiles.MerchantArchetype;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class SyntheticPaymentFeatureBuilderTest {

    private SyntheticPaymentFeatureBuilder builder;

    private static final LocalDateTime AS_OF    = LocalDateTime.of(2024, 6, 1, 0, 0);
    private static final LocalDate     REG_DATE = LocalDate.of(2024, 1, 1);

    @BeforeEach
    void setUp() {
        builder = new SyntheticPaymentFeatureBuilder();
    }

    // ── computePaymentFeatures ─────────────────────────────────────────────

    @Test
    void paymentFeatures_timingFieldsComputedCorrectly() {
        SyntheticMerchant merchant = merchant();
        UUID mid = merchant.syntheticId();
        UUID orderId = UUID.randomUUID();

        // Single payment: 15 days after invoice, full amount, MPESA
        SyntheticPayment payment = new SyntheticPayment(
                UUID.randomUUID(), orderId, mid,
                BigDecimal.valueOf(20_000),
                LocalDateTime.of(2024, 3, 15, 14, 30),
                "MPESA", 15, false, false);

        SyntheticOrder order = new SyntheticOrder(orderId, mid, UUID.randomUUID(),
                LocalDateTime.of(2024, 3, 1, 10, 0),
                BigDecimal.valueOf(20_000), "DELIVERED", MerchantArchetype.STABLE_PERFORMER);

        SyntheticDataBundle bundle = SyntheticDataBundle.create(
                List.of(merchant), List.of(order), List.of(),
                List.of(payment), List.of(), List.of(), List.of(),
                1L, config());

        PaymentFeatures f = builder.computePaymentFeatures(payment, bundle);

        assertThat(f.daysToPay()).isEqualTo(15.0);
        assertThat(f.isLate()).isFalse();           // 15 <= 30
        assertThat(f.isPartial()).isFalse();
        assertThat(f.paymentMethodEncoded()).isEqualTo("MPESA");
        assertThat(f.hourOfDay()).isEqualTo(14);
        assertThat(f.paymentId()).isEqualTo(payment.syntheticId());
        assertThat(f.merchantId()).isEqualTo(mid);
    }

    @Test
    void paymentFeatures_latePayment_isLateTrue() {
        SyntheticMerchant merchant = merchant();
        UUID mid = merchant.syntheticId();
        UUID orderId = UUID.randomUUID();

        SyntheticPayment payment = new SyntheticPayment(
                UUID.randomUUID(), orderId, mid,
                BigDecimal.valueOf(10_000),
                LocalDateTime.of(2024, 3, 1, 10, 0).plusDays(45),
                "CASH", 45, false, false);

        SyntheticDataBundle bundle = SyntheticDataBundle.create(
                List.of(merchant), List.of(), List.of(),
                List.of(payment), List.of(), List.of(), List.of(),
                1L, config());

        PaymentFeatures f = builder.computePaymentFeatures(payment, bundle);

        assertThat(f.isLate()).isTrue();            // 45 > 30
        assertThat(f.daysToPay()).isEqualTo(45.0);
    }

    @Test
    void paymentFeatures_amountVsInvoiceRatio_fullPayment_returnsOne() {
        SyntheticMerchant merchant = merchant();
        UUID mid = merchant.syntheticId();
        UUID orderId = UUID.randomUUID();

        BigDecimal amount = BigDecimal.valueOf(15_000);
        SyntheticPayment payment = new SyntheticPayment(
                UUID.randomUUID(), orderId, mid, amount,
                LocalDateTime.of(2024, 4, 10, 9, 0),
                "MPESA", 10, false, false);

        SyntheticOrder order = new SyntheticOrder(orderId, mid, UUID.randomUUID(),
                LocalDateTime.of(2024, 4, 1, 10, 0),
                amount, "DELIVERED", MerchantArchetype.STABLE_PERFORMER);

        SyntheticDataBundle bundle = SyntheticDataBundle.create(
                List.of(merchant), List.of(order), List.of(),
                List.of(payment), List.of(), List.of(), List.of(),
                1L, config());

        PaymentFeatures f = builder.computePaymentFeatures(payment, bundle);

        assertThat(f.amountVsInvoiceRatio()).isCloseTo(1.0, within(1e-4));
    }

    @Test
    void paymentFeatures_daysToPayVsMerchantAvg_computedFromAllPayments() {
        SyntheticMerchant merchant = merchant();
        UUID mid = merchant.syntheticId();

        // Two previous payments: 10 and 20 days → avg = 15
        SyntheticPayment p1 = new SyntheticPayment(
                UUID.randomUUID(), UUID.randomUUID(), mid,
                BigDecimal.valueOf(5_000), LocalDateTime.of(2024, 2, 10, 10, 0),
                "MPESA", 10, false, false);
        SyntheticPayment p2 = new SyntheticPayment(
                UUID.randomUUID(), UUID.randomUUID(), mid,
                BigDecimal.valueOf(5_000), LocalDateTime.of(2024, 3, 20, 10, 0),
                "CASH", 20, false, false);
        // Current payment: 25 days → deviation from avg = 25 - 15 = 10
        SyntheticPayment current = new SyntheticPayment(
                UUID.randomUUID(), UUID.randomUUID(), mid,
                BigDecimal.valueOf(5_000), LocalDateTime.of(2024, 4, 25, 10, 0),
                "MPESA", 25, false, false);

        SyntheticDataBundle bundle = SyntheticDataBundle.create(
                List.of(merchant), List.of(), List.of(),
                List.of(p1, p2, current), List.of(), List.of(), List.of(),
                1L, config());

        PaymentFeatures f = builder.computePaymentFeatures(current, bundle);

        // avg of (10, 20, 25) = 18.33...; deviation = 25 - 18.33 ≈ 6.67
        // (all 3 payments included in merchant avg)
        assertThat(f.merchantAvgDaysToPay())
                .isCloseTo((10.0 + 20.0 + 25.0) / 3.0, within(0.01));
        assertThat(f.merchantTotalPayments()).isEqualTo(3);
    }

    @Test
    void paymentFeatures_gapSinceLastPayment_computedCorrectly() {
        SyntheticMerchant merchant = merchant();
        UUID mid = merchant.syntheticId();

        SyntheticPayment p1 = new SyntheticPayment(
                UUID.randomUUID(), UUID.randomUUID(), mid,
                BigDecimal.valueOf(5_000), LocalDateTime.of(2024, 1, 1, 10, 0),
                "MPESA", 5, false, false);
        // Gap of 30 days between p1 and p2
        SyntheticPayment p2 = new SyntheticPayment(
                UUID.randomUUID(), UUID.randomUUID(), mid,
                BigDecimal.valueOf(5_000), LocalDateTime.of(2024, 1, 31, 10, 0),
                "CASH", 5, false, false);

        SyntheticDataBundle bundle = SyntheticDataBundle.create(
                List.of(merchant), List.of(), List.of(),
                List.of(p1, p2), List.of(), List.of(), List.of(),
                1L, config());

        PaymentFeatures f = builder.computePaymentFeatures(p2, bundle);

        assertThat(f.gapSinceLastPaymentDays()).isEqualTo(30);
    }

    // ── computeMerchantTrendFeatures ───────────────────────────────────────

    @Test
    void trendFeatures_noPayments_returnsZeroRates() {
        SyntheticMerchant merchant = merchant();
        SyntheticDataBundle bundle = emptyBundle(merchant);

        MerchantPaymentTrendFeatures f =
                builder.computeMerchantTrendFeatures(merchant, bundle, AS_OF);

        assertThat(f.latePaymentRate3m()).isEqualTo(0.0);
        assertThat(f.partialPaymentFreq3m()).isEqualTo(0.0);
        assertThat(f.daysToPayTrend3m()).isEqualTo(0.0);
    }

    @Test
    void trendFeatures_allLatePayments_lateRateOne() {
        SyntheticMerchant merchant = merchant();
        UUID mid = merchant.syntheticId();

        // Three late payments in the last 3 months
        SyntheticPayment p1 = new SyntheticPayment(
                UUID.randomUUID(), UUID.randomUUID(), mid,
                BigDecimal.valueOf(5_000), AS_OF.minusMonths(1),
                "CASH", 45, false, false);
        SyntheticPayment p2 = new SyntheticPayment(
                UUID.randomUUID(), UUID.randomUUID(), mid,
                BigDecimal.valueOf(5_000), AS_OF.minusMonths(2),
                "CASH", 50, false, false);

        SyntheticDataBundle bundle = SyntheticDataBundle.create(
                List.of(merchant), List.of(), List.of(),
                List.of(p1, p2), List.of(), List.of(), List.of(),
                1L, config());

        MerchantPaymentTrendFeatures f =
                builder.computeMerchantTrendFeatures(merchant, bundle, AS_OF);

        assertThat(f.latePaymentRate3m()).isEqualTo(1.0);
    }

    @Test
    void trendFeatures_consecutiveMissedOrders_correctCount() {
        SyntheticMerchant merchant = merchant();
        UUID mid = merchant.syntheticId();

        // Last order was 3 weeks ago — so 3 consecutive missed weeks
        SyntheticOrder lastOrder = new SyntheticOrder(
                UUID.randomUUID(), mid, UUID.randomUUID(),
                AS_OF.minusWeeks(3).minusDays(1), BigDecimal.valueOf(10_000),
                "DELIVERED", MerchantArchetype.STABLE_PERFORMER);

        SyntheticDataBundle bundle = SyntheticDataBundle.create(
                List.of(merchant), List.of(lastOrder), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                1L, config());

        MerchantPaymentTrendFeatures f =
                builder.computeMerchantTrendFeatures(merchant, bundle, AS_OF);

        assertThat(f.consecutiveMissedOrders()).isEqualTo(3);
    }

    @Test
    void trendFeatures_paymentToOrderRatio_correctWhenEqual() {
        SyntheticMerchant merchant = merchant();
        UUID mid = merchant.syntheticId();

        SyntheticOrder order = new SyntheticOrder(
                UUID.randomUUID(), mid, UUID.randomUUID(),
                AS_OF.minusMonths(1), BigDecimal.valueOf(10_000),
                "DELIVERED", MerchantArchetype.STABLE_PERFORMER);

        SyntheticPayment payment = new SyntheticPayment(
                UUID.randomUUID(), order.syntheticId(), mid,
                BigDecimal.valueOf(10_000), AS_OF.minusMonths(1).plusDays(10),
                "MPESA", 10, false, false);

        SyntheticDataBundle bundle = SyntheticDataBundle.create(
                List.of(merchant), List.of(order), List.of(),
                List.of(payment), List.of(), List.of(), List.of(),
                1L, config());

        MerchantPaymentTrendFeatures f =
                builder.computeMerchantTrendFeatures(merchant, bundle, AS_OF);

        assertThat(f.paymentToOrderRatio3m()).isCloseTo(1.0, within(1e-4));
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private SyntheticMerchant merchant() {
        return new SyntheticMerchant(
                UUID.randomUUID(), "Test Shop", "Hardware Store", "Nairobi", "CBD",
                -1.2921, 36.8219, REG_DATE, BigDecimal.valueOf(50_000),
                MerchantArchetype.STABLE_PERFORMER);
    }

    private SyntheticDataBundle emptyBundle(SyntheticMerchant merchant) {
        return SyntheticDataBundle.create(
                List.of(merchant),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                1L, config());
    }

    private SyntheticDataConfig config() {
        return SyntheticDataConfig.defaultConfig(UUID.randomUUID(), 42L);
    }
}
