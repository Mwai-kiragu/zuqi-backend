package com.zuqi.ai.synthetic;

import com.zuqi.ai.synthetic.dto.*;

import com.zuqi.ai.feature.SalesRepFeatures;
import com.zuqi.ai.synthetic.profiles.MerchantArchetype;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class SyntheticSalesRepFeatureBuilderTest {

    private SyntheticSalesRepFeatureBuilder builder;

    private static final UUID              REP_ID      = UUID.randomUUID();
    private static final LocalDateTime     PERIOD_START = LocalDateTime.of(2024, 3, 1, 0, 0);
    private static final LocalDateTime     PERIOD_END   = LocalDateTime.of(2024, 3, 31, 23, 59);

    @BeforeEach
    void setUp() {
        builder = new SyntheticSalesRepFeatureBuilder();
    }

    // ── No activities ──────────────────────────────────────────────────────

    @Test
    void noActivities_returnsZeroMetrics() {
        SyntheticDataBundle bundle = emptyBundle();

        SalesRepFeatures f = builder.computeFeatures(REP_ID, PERIOD_START, PERIOD_END, bundle);

        assertThat(f.salesRepId()).isEqualTo(REP_ID);
        assertThat(f.visitCount()).isZero();
        assertThat(f.ordersCreated()).isZero();
        assertThat(f.totalOrderValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(f.activeMerchants()).isZero();
        assertThat(f.visitTarget()).isZero();    // 0 merchants × weeks
    }

    // ── Visit and conversion metrics ───────────────────────────────────────

    @Test
    void visitCount_matchesActivitiesInPeriod() {
        UUID m1 = UUID.randomUUID();
        UUID m2 = UUID.randomUUID();

        List<SyntheticRepActivity> activities = List.of(
                activity(m1, LocalDate.of(2024, 3, 5), false, BigDecimal.ZERO),
                activity(m2, LocalDate.of(2024, 3, 10), true, BigDecimal.valueOf(10_000)),
                activity(m1, LocalDate.of(2024, 3, 15), true, BigDecimal.valueOf(8_000)),
                // Outside period:
                activity(m1, LocalDate.of(2024, 2, 28), true, BigDecimal.valueOf(5_000))
        );

        SyntheticDataBundle bundle = bundleWithActivities(activities);

        SalesRepFeatures f = builder.computeFeatures(REP_ID, PERIOD_START, PERIOD_END, bundle);

        assertThat(f.visitCount()).isEqualTo(3);   // only 3 in March
    }

    @Test
    void ordersCreated_onlyFromActivitiesWithOrderPlaced() {
        UUID m1 = UUID.randomUUID();

        List<SyntheticRepActivity> activities = List.of(
                activity(m1, LocalDate.of(2024, 3, 5), false, BigDecimal.ZERO),
                activity(m1, LocalDate.of(2024, 3, 12), true, BigDecimal.valueOf(10_000)),
                activity(m1, LocalDate.of(2024, 3, 20), true, BigDecimal.valueOf(15_000))
        );

        SalesRepFeatures f = builder.computeFeatures(
                REP_ID, PERIOD_START, PERIOD_END, bundleWithActivities(activities));

        assertThat(f.ordersCreated()).isEqualTo(2);      // 2 activities with orderPlaced=true
        assertThat(f.visitCount()).isEqualTo(3);
        assertThat(f.orderConversionRate()).isCloseTo(66.67, within(0.01));
    }

    // ── Order value metrics ────────────────────────────────────────────────

    @Test
    void totalOrderValue_sumOfOrderedActivities() {
        UUID m1 = UUID.randomUUID();

        List<SyntheticRepActivity> activities = List.of(
                activity(m1, LocalDate.of(2024, 3, 5), true, BigDecimal.valueOf(10_000)),
                activity(m1, LocalDate.of(2024, 3, 12), true, BigDecimal.valueOf(20_000)),
                activity(m1, LocalDate.of(2024, 3, 20), false, BigDecimal.ZERO)
        );

        SalesRepFeatures f = builder.computeFeatures(
                REP_ID, PERIOD_START, PERIOD_END, bundleWithActivities(activities));

        assertThat(f.totalOrderValue()).isEqualByComparingTo(BigDecimal.valueOf(30_000));
        assertThat(f.avgOrderValue()).isEqualByComparingTo(BigDecimal.valueOf(15_000));
    }

    // ── Merchant metrics ───────────────────────────────────────────────────

    @Test
    void activeMerchants_distinctMerchantsInPeriod() {
        UUID m1 = UUID.randomUUID();
        UUID m2 = UUID.randomUUID();

        List<SyntheticRepActivity> activities = List.of(
                activity(m1, LocalDate.of(2024, 3, 5), true, BigDecimal.valueOf(5_000)),
                activity(m1, LocalDate.of(2024, 3, 12), true, BigDecimal.valueOf(5_000)),
                activity(m2, LocalDate.of(2024, 3, 15), true, BigDecimal.valueOf(8_000))
        );

        SalesRepFeatures f = builder.computeFeatures(
                REP_ID, PERIOD_START, PERIOD_END, bundleWithActivities(activities));

        assertThat(f.activeMerchants()).isEqualTo(2);   // m1 and m2
    }

    @Test
    void merchantRetentionRate_allMerchantsOrdered_returns100() {
        UUID m1 = UUID.randomUUID();
        UUID m2 = UUID.randomUUID();

        List<SyntheticRepActivity> activities = List.of(
                activity(m1, LocalDate.of(2024, 3, 5), true, BigDecimal.valueOf(5_000)),
                activity(m2, LocalDate.of(2024, 3, 10), true, BigDecimal.valueOf(8_000))
        );

        SalesRepFeatures f = builder.computeFeatures(
                REP_ID, PERIOD_START, PERIOD_END, bundleWithActivities(activities));

        assertThat(f.merchantRetentionRate()).isEqualTo(100.0);
    }

    @Test
    void merchantRetentionRate_halfMerchantsOrdered_returns50() {
        UUID m1 = UUID.randomUUID();
        UUID m2 = UUID.randomUUID();

        List<SyntheticRepActivity> activities = List.of(
                activity(m1, LocalDate.of(2024, 3, 5), true, BigDecimal.valueOf(5_000)),
                activity(m2, LocalDate.of(2024, 3, 10), false, BigDecimal.ZERO)
        );

        SalesRepFeatures f = builder.computeFeatures(
                REP_ID, PERIOD_START, PERIOD_END, bundleWithActivities(activities));

        assertThat(f.merchantRetentionRate()).isEqualTo(50.0);
    }

    // ── Visit target ───────────────────────────────────────────────────────

    @Test
    void visitTarget_oneMerchantFor4Weeks_is4() {
        UUID m1 = UUID.randomUUID();

        List<SyntheticRepActivity> activities = List.of(
                activity(m1, LocalDate.of(2024, 3, 1), true, BigDecimal.valueOf(5_000))
        );

        SalesRepFeatures f = builder.computeFeatures(
                REP_ID, PERIOD_START, PERIOD_END, bundleWithActivities(activities));

        // Period = 30 days → ceil(30/7) = 5 weeks → 1 merchant × 5 = 5
        assertThat(f.visitTarget()).isEqualTo(5);
    }

    // ── Metadata ───────────────────────────────────────────────────────────

    @Test
    void metadata_periodAndRepIdPreserved() {
        SalesRepFeatures f = builder.computeFeatures(
                REP_ID, PERIOD_START, PERIOD_END, emptyBundle());

        assertThat(f.salesRepId()).isEqualTo(REP_ID);
        assertThat(f.periodStart()).isEqualTo(PERIOD_START);
        assertThat(f.periodEnd()).isEqualTo(PERIOD_END);
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private SyntheticRepActivity activity(UUID merchantRef, LocalDate visitDate,
                                           boolean orderPlaced, BigDecimal orderValue) {
        return new SyntheticRepActivity(
                UUID.randomUUID(), REP_ID, merchantRef,
                visitDate, orderPlaced, orderValue, 30, false);
    }

    private SyntheticDataBundle bundleWithActivities(List<SyntheticRepActivity> activities) {
        return SyntheticDataBundle.create(
                List.of(), List.of(), List.of(), List.of(), List.of(),
                activities, List.of(), List.of(),
                1L, SyntheticDataConfig.defaultConfig(UUID.randomUUID(), 42L));
    }

    private SyntheticDataBundle emptyBundle() {
        return SyntheticDataBundle.create(
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(),
                1L, SyntheticDataConfig.defaultConfig(UUID.randomUUID(), 42L));
    }
}
