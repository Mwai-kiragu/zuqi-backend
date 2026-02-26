package com.zuqi.ai.synthetic.profiles;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class MerchantArchetypeTest {

    private static final Random RNG = new Random(42);

    // -------------------------------------------------------------------------
    // Population ratios
    // -------------------------------------------------------------------------

    @Test
    void populationRatios_shouldSumToOne() {
        double sum = Arrays.stream(MerchantArchetype.values())
                .mapToDouble(a -> a.populationRatio)
                .sum();
        assertThat(sum).isCloseTo(1.0, offset(0.001));
    }

    @Test
    void defaulter_shouldHaveLowestPopulationRatio() {
        assertThat(MerchantArchetype.DEFAULTER.populationRatio)
                .isLessThan(MerchantArchetype.STEADY_GROWER.populationRatio);
    }

    // -------------------------------------------------------------------------
    // Sampling — values should stay within plausible bounds
    // -------------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(MerchantArchetype.class)
    void sampleOrdersPerWeek_shouldNeverBeNegative(MerchantArchetype archetype) {
        for (int i = 0; i < 1000; i++) {
            assertThat(archetype.sampleOrdersPerWeek(RNG)).isGreaterThanOrEqualTo(0.0);
        }
    }

    @ParameterizedTest
    @EnumSource(MerchantArchetype.class)
    void sampleOrderValueKes_shouldNeverBelowMinimum(MerchantArchetype archetype) {
        for (int i = 0; i < 1000; i++) {
            assertThat(archetype.sampleOrderValueKes(RNG)).isGreaterThanOrEqualTo(500.0);
        }
    }

    @ParameterizedTest
    @EnumSource(MerchantArchetype.class)
    void samplePaymentDays_shouldNeverBeNegative(MerchantArchetype archetype) {
        for (int i = 0; i < 1000; i++) {
            assertThat(archetype.samplePaymentDays(RNG)).isGreaterThanOrEqualTo(0);
        }
    }

    // -------------------------------------------------------------------------
    // Distribution ordering — archetypes should rank sensibly
    // -------------------------------------------------------------------------

    @Test
    void steadyGrower_shouldPayFasterThanDefaulter() {
        assertThat(MerchantArchetype.STEADY_GROWER.paymentDaysMean)
                .isLessThan(MerchantArchetype.DEFAULTER.paymentDaysMean);
    }

    @Test
    void defaulter_shouldHaveHigherDefaultProbabilityThanSteadyGrower() {
        assertThat(MerchantArchetype.DEFAULTER.defaultProbability)
                .isGreaterThan(MerchantArchetype.STEADY_GROWER.defaultProbability);
    }

    @Test
    void steadyGrower_shouldHavePositiveGrowthRate() {
        assertThat(MerchantArchetype.STEADY_GROWER.monthlyGrowthRate).isPositive();
    }

    @Test
    void decliningRisk_shouldHaveNegativeGrowthRate() {
        assertThat(MerchantArchetype.DECLINING_RISK.monthlyGrowthRate).isNegative();
    }

    @Test
    void defaulter_shouldHaveHighDefaultProbability() {
        assertThat(MerchantArchetype.DEFAULTER.defaultProbability).isGreaterThan(0.5);
    }

    @Test
    void stablePerformer_shouldHaveLowestDefaultProbability() {
        double minProb = Arrays.stream(MerchantArchetype.values())
                .mapToDouble(a -> a.defaultProbability)
                .min().orElseThrow();
        assertThat(MerchantArchetype.STABLE_PERFORMER.defaultProbability)
                .isEqualTo(minProb);
    }

    // -------------------------------------------------------------------------
    // Growth application
    // -------------------------------------------------------------------------

    @Test
    void applyGrowth_steadyGrower_shouldIncreaseValueOverTime() {
        double base = 20_000.0;
        double after12Months = MerchantArchetype.STEADY_GROWER.applyGrowth(base, 12);
        assertThat(after12Months).isGreaterThan(base);
    }

    @Test
    void applyGrowth_decliningRisk_shouldDecreaseValueOverTime() {
        double base = 20_000.0;
        double after12Months = MerchantArchetype.DECLINING_RISK.applyGrowth(base, 12);
        assertThat(after12Months).isLessThan(base);
    }

    @Test
    void applyGrowth_zeroMonths_shouldReturnBaseValue() {
        double base = 15_000.0;
        assertThat(MerchantArchetype.STEADY_GROWER.applyGrowth(base, 0))
                .isCloseTo(base, offset(0.01));
    }

    // -------------------------------------------------------------------------
    // Default sampling over large sample — statistical sanity check
    // -------------------------------------------------------------------------

    @Test
    void sampleDefaults_defaulter_shouldDefaultFrequently() {
        int defaults = 0;
        Random rng = new Random(123);
        for (int i = 0; i < 1000; i++) {
            if (MerchantArchetype.DEFAULTER.sampleDefaults(rng)) defaults++;
        }
        // With defaultProbability=0.80, expect roughly 750–850 out of 1000
        assertThat(defaults).isBetween(700, 900);
    }

    @Test
    void sampleDefaults_steadyGrower_shouldRarelyDefault() {
        int defaults = 0;
        Random rng = new Random(123);
        for (int i = 0; i < 1000; i++) {
            if (MerchantArchetype.STEADY_GROWER.sampleDefaults(rng)) defaults++;
        }
        // With defaultProbability=0.02, expect < 50 out of 1000
        assertThat(defaults).isLessThan(60);
    }
}
