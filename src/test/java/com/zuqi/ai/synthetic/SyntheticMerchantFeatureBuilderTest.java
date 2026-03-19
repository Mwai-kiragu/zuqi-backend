package com.zuqi.ai.synthetic;

import com.zuqi.ai.synthetic.dto.*;

import com.zuqi.ai.feature.MerchantFeatures;
import com.zuqi.ai.synthetic.profiles.MerchantArchetype;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class SyntheticMerchantFeatureBuilderTest {

    private SyntheticMerchantFeatureBuilder builder;

    private static final LocalDateTime AS_OF = LocalDateTime.of(2024, 6, 1, 0, 0);
    private static final LocalDate     REG_DATE = LocalDate.of(2024, 1, 1);

    @BeforeEach
    void setUp() {
        builder = new SyntheticMerchantFeatureBuilder();
    }

    // ── Profile features ───────────────────────────────────────────────────

    @Test
    void profileFeatures_mappedCorrectly() {
        SyntheticMerchant merchant = merchant("Hardware Store", "Nairobi");
        SyntheticDataBundle bundle = emptyBundle(merchant);

        MerchantFeatures f = builder.computeFeatures(merchant, bundle, AS_OF);

        assertThat(f.merchantId()).isEqualTo(merchant.syntheticId());
        assertThat(f.businessCategoryEncoded()).isEqualTo("Hardware Store");
        assertThat(f.geographicCluster()).isEqualTo("Nairobi");
        assertThat(f.verificationStatus()).isEqualTo("UNVERIFIED");
        // Tenure: Jan 1 to Jun 1 = 152 days
        assertThat(f.relationshipTenureDays()).isEqualTo(152);
    }

    // ── Order features — empty orders ──────────────────────────────────────

    @Test
    void orderFeatures_noOrders_returnsDefaults() {
        SyntheticMerchant merchant = merchant("General Store", "Mombasa");
        SyntheticDataBundle bundle = emptyBundle(merchant);

        MerchantFeatures f = builder.computeFeatures(merchant, bundle, AS_OF);

        assertThat(f.totalOrders()).isZero();
        assertThat(f.orderFrequencyPerWeek()).isEqualTo(0.0);
        assertThat(f.avgOrderValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(f.cancellationRate()).isEqualTo(0.0);
        assertThat(f.daysSinceLastOrder()).isEqualTo(Integer.MAX_VALUE);
        assertThat(f.uniqueSkusOrdered()).isZero();
        assertThat(f.topSkuConcentration()).isEqualTo(0.0);
    }

    @Test
    void orderFeatures_twoOrders_computedCorrectly() {
        SyntheticMerchant merchant = merchant("Supermarket", "Kisumu");
        UUID mid = merchant.syntheticId();
        UUID sku1 = UUID.randomUUID();
        UUID sku2 = UUID.randomUUID();

        // Two orders: one on Jan 1 (5000), one on Feb 1 (15000)
        SyntheticOrder o1 = order(mid, BigDecimal.valueOf(5_000),
                LocalDateTime.of(2024, 1, 1, 10, 0), "DELIVERED");
        SyntheticOrder o2 = order(mid, BigDecimal.valueOf(15_000),
                LocalDateTime.of(2024, 2, 1, 10, 0), "DELIVERED");

        SyntheticOrderItem item1 = new SyntheticOrderItem(
                o1.syntheticId(), sku1, BigDecimal.ONE, BigDecimal.valueOf(5_000), BigDecimal.valueOf(5_000));
        SyntheticOrderItem item2 = new SyntheticOrderItem(
                o2.syntheticId(), sku2, BigDecimal.ONE, BigDecimal.valueOf(15_000), BigDecimal.valueOf(15_000));

        SyntheticDataBundle bundle = SyntheticDataBundle.create(
                List.of(merchant),
                List.of(o1, o2),
                List.of(item1, item2),
                List.of(), List.of(), List.of(), List.of(),
                List.of(),
                42L, config());

        MerchantFeatures f = builder.computeFeatures(merchant, bundle, AS_OF);

        assertThat(f.totalOrders()).isEqualTo(2);
        assertThat(f.avgOrderValue()).isEqualByComparingTo(BigDecimal.valueOf(10_000));
        assertThat(f.uniqueSkusOrdered()).isEqualTo(2);
        assertThat(f.cancellationRate()).isEqualTo(0.0);
    }

    @Test
    void orderFeatures_cancelledOrder_cancellationRateCorrect() {
        SyntheticMerchant merchant = merchant("Kiosk", "Nakuru");
        UUID mid = merchant.syntheticId();

        SyntheticOrder o1 = order(mid, BigDecimal.valueOf(5_000),
                LocalDateTime.of(2024, 3, 1, 10, 0), "DELIVERED");
        SyntheticOrder o2 = order(mid, BigDecimal.valueOf(5_000),
                LocalDateTime.of(2024, 3, 15, 10, 0), "CANCELLED");

        SyntheticDataBundle bundle = SyntheticDataBundle.create(
                List.of(merchant), List.of(o1, o2),
                List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(),
                1L, config());

        MerchantFeatures f = builder.computeFeatures(merchant, bundle, AS_OF);

        assertThat(f.cancellationRate()).isEqualTo(0.5);  // 1/2
    }

    // ── Payment features — empty payments ─────────────────────────────────

    @Test
    void paymentFeatures_noPayments_returnsDefaults() {
        SyntheticMerchant merchant = merchant("Grocery", "Eldoret");
        SyntheticDataBundle bundle = emptyBundle(merchant);

        MerchantFeatures f = builder.computeFeatures(merchant, bundle, AS_OF);

        assertThat(f.totalPayments()).isZero();
        assertThat(f.onTimePaymentPct()).isEqualTo(1.0);     // no payments → assumed on time
        assertThat(f.avgDaysToPay()).isEqualTo(0.0);
        assertThat(f.worstDaysToPay()).isEqualTo(0);
        assertThat(f.partialPaymentFrequency()).isEqualTo(0.0);
        assertThat(f.consecutiveOnTimeStreak()).isZero();
    }

    @Test
    void paymentFeatures_twoPayments_computedCorrectly() {
        SyntheticMerchant merchant = merchant("Hardware Store", "Nairobi");
        UUID mid = merchant.syntheticId();
        UUID inv1 = UUID.randomUUID();
        UUID inv2 = UUID.randomUUID();

        // On-time (5 days) and late (45 days) payments
        SyntheticPayment p1 = payment(mid, inv1, 5, false, false, "MPESA");
        SyntheticPayment p2 = payment(mid, inv2, 45, false, false, "CASH");

        SyntheticDataBundle bundle = SyntheticDataBundle.create(
                List.of(merchant), List.of(), List.of(),
                List.of(p1, p2), List.of(), List.of(), List.of(),
                List.of(),
                1L, config());

        MerchantFeatures f = builder.computeFeatures(merchant, bundle, AS_OF);

        assertThat(f.totalPayments()).isEqualTo(2);
        assertThat(f.onTimePaymentPct()).isEqualTo(0.5);     // 1/2 on time
        assertThat(f.avgDaysToPay()).isEqualTo(25.0);        // (5 + 45) / 2
        assertThat(f.worstDaysToPay()).isEqualTo(45);
    }

    @Test
    void paymentFeatures_allPartial_partialFrequencyOne() {
        SyntheticMerchant merchant = merchant("General Store", "Mombasa");
        UUID mid = merchant.syntheticId();

        SyntheticPayment p1 = payment(mid, UUID.randomUUID(), 10, true, false, "MPESA");
        SyntheticPayment p2 = payment(mid, UUID.randomUUID(), 12, true, false, "MPESA");

        SyntheticDataBundle bundle = SyntheticDataBundle.create(
                List.of(merchant), List.of(), List.of(),
                List.of(p1, p2), List.of(), List.of(), List.of(),
                List.of(),
                1L, config());

        MerchantFeatures f = builder.computeFeatures(merchant, bundle, AS_OF);

        assertThat(f.partialPaymentFrequency()).isEqualTo(1.0);
    }

    // ── Credit features ────────────────────────────────────────────────────

    @Test
    void creditFeatures_noCreditHistory_usesInitialLimit() {
        BigDecimal initialLimit = BigDecimal.valueOf(100_000);
        SyntheticMerchant merchant = new SyntheticMerchant(
                UUID.randomUUID(), "Test Shop", "Retail", "Nairobi", "Westlands",
                -1.2921, 36.8219, REG_DATE, initialLimit, MerchantArchetype.STABLE_PERFORMER);
        SyntheticDataBundle bundle = emptyBundle(merchant);

        MerchantFeatures f = builder.computeFeatures(merchant, bundle, AS_OF);

        assertThat(f.currentCreditLimit()).isEqualByComparingTo(initialLimit);
        assertThat(f.limitIncreaseCount()).isZero();
        assertThat(f.daysSinceLastLimitChange()).isZero();
    }

    @Test
    void creditFeatures_withCreditHistory_usesLatestLimit() {
        SyntheticMerchant merchant = merchant("Supermarket", "Nairobi");
        UUID mid = merchant.syntheticId();

        SyntheticCreditEvaluation eval1 = new SyntheticCreditEvaluation(
                UUID.randomUUID(), mid, LocalDate.of(2024, 2, 1),
                "B", BigDecimal.valueOf(80_000), false, null);
        SyntheticCreditEvaluation eval2 = new SyntheticCreditEvaluation(
                UUID.randomUUID(), mid, LocalDate.of(2024, 4, 1),
                "A", BigDecimal.valueOf(120_000), false, null);

        SyntheticDataBundle bundle = SyntheticDataBundle.create(
                List.of(merchant), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(eval1, eval2), List.of(), 1L, config());

        MerchantFeatures f = builder.computeFeatures(merchant, bundle, AS_OF);

        assertThat(f.currentCreditLimit()).isEqualByComparingTo(BigDecimal.valueOf(120_000));
        assertThat(f.limitIncreaseCount()).isEqualTo(1);   // 80k → 120k = 1 increase
    }

    // ── Null-safety (no NPE for downstream CreditMlFeatureBuilder) ─────────

    @Test
    void noNullNumericFields_forDownstreamSafety() {
        SyntheticMerchant merchant = merchant("Hardware Store", "Nairobi");
        SyntheticDataBundle bundle = emptyBundle(merchant);

        MerchantFeatures f = builder.computeFeatures(merchant, bundle, AS_OF);

        assertThat(f.onTimePaymentPct()).isNotNull();
        assertThat(f.avgDaysToPay()).isNotNull();
        assertThat(f.worstDaysToPay()).isNotNull();
        assertThat(f.peakUtilizationRatio()).isNotNull();
        assertThat(f.daysSinceLastLimitChange()).isNotNull();
        assertThat(f.currentUtilizationRatio()).isNotNull();
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private SyntheticMerchant merchant(String category, String county) {
        return new SyntheticMerchant(
                UUID.randomUUID(), "Test Merchant", category, county, "CBD",
                -1.2921, 36.8219, REG_DATE, BigDecimal.valueOf(50_000),
                MerchantArchetype.STABLE_PERFORMER);
    }

    private SyntheticOrder order(UUID merchantRef, BigDecimal amount,
                                  LocalDateTime date, String status) {
        return new SyntheticOrder(UUID.randomUUID(), merchantRef, UUID.randomUUID(),
                date, amount, status, MerchantArchetype.STABLE_PERFORMER);
    }

    private SyntheticPayment payment(UUID merchantRef, UUID invoiceRef,
                                      int daysAfterInvoice, boolean isPartial,
                                      boolean isDefault, String method) {
        return new SyntheticPayment(
                UUID.randomUUID(), invoiceRef, merchantRef,
                BigDecimal.valueOf(10_000),
                LocalDateTime.of(2024, 3, 1, 10, 0).plusDays(daysAfterInvoice),
                method, daysAfterInvoice, isPartial, isDefault);
    }

    private SyntheticDataBundle emptyBundle(SyntheticMerchant merchant) {
        return SyntheticDataBundle.create(
                List.of(merchant),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(),
                1L, config());
    }

    private SyntheticDataConfig config() {
        return SyntheticDataConfig.defaultConfig(UUID.randomUUID(), 42L);
    }
}
