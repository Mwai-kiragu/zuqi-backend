package com.zuqi.ai.crm;

import com.zuqi.ai.synthetic.SyntheticDataBundle;
import com.zuqi.ai.synthetic.SyntheticDataConfig;
import com.zuqi.ai.synthetic.dto.SyntheticMerchant;
import com.zuqi.ai.synthetic.dto.SyntheticOrder;
import com.zuqi.ai.synthetic.dto.SyntheticPayment;
import com.zuqi.ai.synthetic.profiles.MerchantArchetype;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit test — no Spring context, no mocks.
 */
class SyntheticCustomerAnalyticsFeatureBuilderTest {

    private SyntheticCustomerAnalyticsFeatureBuilder builder;
    private LocalDateTime asOf;

    @BeforeEach
    void setUp() {
        builder = new SyntheticCustomerAnalyticsFeatureBuilder();
        asOf = LocalDateTime.now();
    }

    private SyntheticMerchant merchant() {
        return new SyntheticMerchant(
                UUID.randomUUID(), "Test Shop", "retail",
                "Nairobi", "Westlands",
                -1.28, 36.82,
                LocalDate.now().minusMonths(12),
                BigDecimal.valueOf(50_000),
                MerchantArchetype.STEADY_GROWER
        );
    }

    private SyntheticDataBundle emptyBundle(SyntheticMerchant m) {
        return SyntheticDataBundle.create(
                List.of(m), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(),
                42L, SyntheticDataConfig.defaultConfig(null, 42L));
    }

    @Test
    void emptyBundle_returnsZeroRevenue() {
        SyntheticMerchant m = merchant();
        SyntheticDataBundle bundle = emptyBundle(m);

        CustomerAnalyticsFeatures f = builder.computeFeatures(m, bundle, asOf);

        assertThat(f.totalRevenue90d()).isEqualTo(0.0);
        assertThat(f.lifetimeRevenue()).isEqualTo(0.0);
        assertThat(f.orderCount90d()).isEqualTo(0.0);
        assertThat(f.daysSinceLastOrder()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void emptyBundle_paymentTimeliness_defaultsTo100() {
        SyntheticMerchant m = merchant();
        SyntheticDataBundle bundle = emptyBundle(m);

        CustomerAnalyticsFeatures f = builder.computeFeatures(m, bundle, asOf);

        assertThat(f.paymentTimelinessScore()).isEqualTo(100.0);
    }

    @Test
    void tenureComputedFromRegistrationDate() {
        SyntheticMerchant m = merchant(); // registered 12 months ago
        SyntheticDataBundle bundle = emptyBundle(m);

        CustomerAnalyticsFeatures f = builder.computeFeatures(m, bundle, asOf);

        assertThat(f.tenureMonths()).isGreaterThanOrEqualTo(11);
    }

    @Test
    void withOneOrder_revenueAndFrequencyComputed() {
        SyntheticMerchant m = merchant();
        UUID orderId = UUID.randomUUID();
        SyntheticOrder order = new SyntheticOrder(
                orderId, m.syntheticId(), UUID.randomUUID(),
                asOf.minusDays(10), BigDecimal.valueOf(15_000),
                "DELIVERED", MerchantArchetype.STEADY_GROWER
        );

        SyntheticDataBundle bundle = SyntheticDataBundle.create(
                List.of(m), List.of(order), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(),
                42L, SyntheticDataConfig.defaultConfig(null, 42L));

        CustomerAnalyticsFeatures f = builder.computeFeatures(m, bundle, asOf);

        assertThat(f.totalRevenue90d()).isEqualTo(15_000.0);
        assertThat(f.lifetimeRevenue()).isEqualTo(15_000.0);
        assertThat(f.orderCount90d()).isEqualTo(1.0);
    }

    @Test
    void cancelledOrdersExcludedFromRevenue() {
        SyntheticMerchant m = merchant();
        SyntheticOrder active = new SyntheticOrder(
                UUID.randomUUID(), m.syntheticId(), UUID.randomUUID(),
                asOf.minusDays(5), BigDecimal.valueOf(10_000),
                "DELIVERED", MerchantArchetype.STEADY_GROWER);
        SyntheticOrder cancelled = new SyntheticOrder(
                UUID.randomUUID(), m.syntheticId(), UUID.randomUUID(),
                asOf.minusDays(3), BigDecimal.valueOf(50_000),
                "CANCELLED", MerchantArchetype.STEADY_GROWER);

        SyntheticDataBundle bundle = SyntheticDataBundle.create(
                List.of(m), List.of(active, cancelled), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(),
                42L, SyntheticDataConfig.defaultConfig(null, 42L));

        CustomerAnalyticsFeatures f = builder.computeFeatures(m, bundle, asOf);

        assertThat(f.totalRevenue90d()).isEqualTo(10_000.0);
    }

    @Test
    void customerCategoryFromMerchantBusinessCategory() {
        SyntheticMerchant m = merchant();
        SyntheticDataBundle bundle = emptyBundle(m);

        CustomerAnalyticsFeatures f = builder.computeFeatures(m, bundle, asOf);

        assertThat(f.customerCategory()).isEqualTo("retail");
    }
}
