package com.zuqi.ai.training;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test synthetic merchant data generation.
 *
 * Blueprint: ML_IMPLEMENTATION_PLAN.md Task 1.4
 */
@SpringBootTest
@ActiveProfiles("test")
class SyntheticMerchantDataGeneratorTest {

    @Autowired
    private SyntheticMerchantDataGenerator generator;

    @Test
    void testGenerateSmallDataset() {
        // Generate 100 merchants
        List<SyntheticMerchant> merchants = generator.generateDataset(100);

        assertThat(merchants).hasSize(100);
        assertThat(merchants).allMatch(m -> m.features() != null);
        assertThat(merchants).allMatch(m -> m.archetypeName() != null);
    }

    @Test
    void testDatasetQuality() {
        // Generate larger dataset for quality validation
        List<SyntheticMerchant> merchants = generator.generateDataset(1000);

        // Validate quality
        SyntheticMerchantDataGenerator.DatasetQualityReport report =
                generator.validateDataset(merchants);

        // Assert quality criteria
        assertThat(report.totalMerchants()).isEqualTo(1000);
        assertThat(report.defaultRate()).isBetween(0.12, 0.18);
        assertThat(report.defaultRateOk()).isTrue();
        assertThat(report.allFeaturesPopulated()).isTrue();
        assertThat(report.realisticValues()).isTrue();
        assertThat(report.correlationOk()).isTrue();
        assertThat(report.isValid()).isTrue();
    }

    @Test
    void testFeatureCompleteness() {
        List<SyntheticMerchant> merchants = generator.generateDataset(50);

        for (SyntheticMerchant merchant : merchants) {
            var features = merchant.features();

            // Order features
            assertThat(features.totalOrders()).isGreaterThan(0);
            assertThat(features.orderFrequencyPerWeek()).isGreaterThan(0.0);
            assertThat(features.avgOrderValue()).isGreaterThan(java.math.BigDecimal.ZERO);

            // Payment features
            assertThat(features.onTimePaymentPct()).isBetween(0.0, 1.0);
            assertThat(features.avgDaysToPay()).isGreaterThan(0.0);
            assertThat(features.paymentMethodDistribution()).isNotEmpty();

            // Credit features
            assertThat(features.currentCreditLimit()).isGreaterThan(java.math.BigDecimal.ZERO);
            assertThat(features.currentUtilizationRatio()).isBetween(0.0, 1.05);

            // Profile features
            assertThat(features.businessCategoryEncoded()).isNotNull();
            assertThat(features.relationshipTenureDays()).isGreaterThan(0);
            assertThat(features.verificationStatus()).isNotNull();
            assertThat(features.geographicCluster()).isNotNull();
        }
    }

    @Test
    void testDefaultDistribution() {
        List<SyntheticMerchant> merchants = generator.generateDataset(1000);

        long defaultCount = merchants.stream()
                .filter(SyntheticMerchant::didDefault)
                .count();

        double defaultRate = (double) defaultCount / merchants.size();

        // Expected ~15% default rate (12-18% acceptable)
        assertThat(defaultRate).isBetween(0.12, 0.18);

        System.out.println("Generated 1000 merchants with " +
                String.format("%.1f%%", defaultRate * 100) + " default rate");
    }

    @Test
    void testArchetypeDistribution() {
        List<SyntheticMerchant> merchants = generator.generateDataset(1000);

        // Count each archetype
        long excellent = merchants.stream()
                .filter(m -> m.archetypeName().equals("Excellent Retailer"))
                .count();
        long good = merchants.stream()
                .filter(m -> m.archetypeName().equals("Good Hardware Store"))
                .count();
        long average = merchants.stream()
                .filter(m -> m.archetypeName().equals("Average Shop"))
                .count();
        long risky = merchants.stream()
                .filter(m -> m.archetypeName().equals("Risky Newcomer"))
                .count();
        long struggling = merchants.stream()
                .filter(m -> m.archetypeName().equals("Struggling Business"))
                .count();
        long highRisk = merchants.stream()
                .filter(m -> m.archetypeName().equals("High Default Risk"))
                .count();

        System.out.println("Archetype distribution:");
        System.out.println("  Excellent Retailer: " + excellent + " (~40% expected)");
        System.out.println("  Good Hardware Store: " + good + " (~30% expected)");
        System.out.println("  Average Shop: " + average + " (~20% expected)");
        System.out.println("  Risky Newcomer: " + risky + " (~7% expected)");
        System.out.println("  Struggling Business: " + struggling + " (~2% expected)");
        System.out.println("  High Default Risk: " + highRisk + " (~1% expected)");

        // Roughly correct proportions (allow some variance)
        assertThat(excellent).isBetween(350L, 450L); // ~40%
        assertThat(good).isBetween(250L, 350L);      // ~30%
        assertThat(average).isBetween(150L, 250L);   // ~20%
    }

    @Test
    void testGenerationPerformance() {
        // Should generate 10k merchants in <5 seconds
        long startTime = System.currentTimeMillis();
        List<SyntheticMerchant> merchants = generator.generateDataset(10_000);
        long duration = System.currentTimeMillis() - startTime;

        assertThat(merchants).hasSize(10_000);
        assertThat(duration).isLessThan(5000); // <5 seconds

        System.out.println("Generated 10,000 merchants in " + duration + "ms");
    }

    @Test
    void testRealisticCorrelations() {
        List<SyntheticMerchant> merchants = generator.generateDataset(1000);

        // Merchants with high on-time payment should default less
        double highOnTimeDefaultRate = merchants.stream()
                .filter(m -> m.features().onTimePaymentPct() > 0.85)
                .filter(SyntheticMerchant::didDefault)
                .count() / (double) merchants.stream()
                .filter(m -> m.features().onTimePaymentPct() > 0.85)
                .count();

        double lowOnTimeDefaultRate = merchants.stream()
                .filter(m -> m.features().onTimePaymentPct() < 0.60)
                .filter(SyntheticMerchant::didDefault)
                .count() / (double) merchants.stream()
                .filter(m -> m.features().onTimePaymentPct() < 0.60)
                .count();

        System.out.println("High on-time payment (>85%) default rate: " +
                String.format("%.1f%%", highOnTimeDefaultRate * 100));
        System.out.println("Low on-time payment (<60%) default rate: " +
                String.format("%.1f%%", lowOnTimeDefaultRate * 100));

        // Low on-time should have significantly higher default rate
        assertThat(lowOnTimeDefaultRate).isGreaterThan(highOnTimeDefaultRate + 0.20);
    }
}
