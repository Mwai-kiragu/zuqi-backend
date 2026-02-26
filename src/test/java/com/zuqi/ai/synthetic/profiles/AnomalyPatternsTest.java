package com.zuqi.ai.synthetic.profiles;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnomalyPatternsTest {

    // -------------------------------------------------------------------------
    // ShrinkagePattern
    // -------------------------------------------------------------------------

    @Test
    void sudden_shouldHaveHigherQuantityFactor_thanGradual() {
        assertThat(AnomalyPatterns.ShrinkagePattern.SUDDEN.quantityFactor)
                .isGreaterThan(AnomalyPatterns.ShrinkagePattern.GRADUAL.quantityFactor);
    }

    @Test
    void gradual_shouldSpanMoreDays_thanSudden() {
        assertThat(AnomalyPatterns.ShrinkagePattern.GRADUAL.burstDays)
                .isGreaterThan(AnomalyPatterns.ShrinkagePattern.SUDDEN.burstDays);
    }

    @Test
    void shrinkagePatterns_injectionRates_shouldBeLow() {
        for (AnomalyPatterns.ShrinkagePattern p : AnomalyPatterns.ShrinkagePattern.values()) {
            assertThat(p.injectionRate)
                    .as("Injection rate for %s should be low (< 0.10)", p)
                    .isLessThan(0.10);
        }
    }

    @Test
    void shrinkagePatterns_quantityFactors_shouldBeAboveOne() {
        for (AnomalyPatterns.ShrinkagePattern p : AnomalyPatterns.ShrinkagePattern.values()) {
            assertThat(p.quantityFactor)
                    .as("Quantity factor for %s should amplify normal movement", p)
                    .isGreaterThan(1.0);
        }
    }

    // -------------------------------------------------------------------------
    // PaymentDistressPattern
    // -------------------------------------------------------------------------

    @Test
    void missedPayments_shouldActivateLater_thanDeterioratingTiming() {
        assertThat(AnomalyPatterns.PaymentDistressPattern.MISSED_PAYMENTS.activationMonth)
                .isGreaterThan(AnomalyPatterns.PaymentDistressPattern.DETERIORATING_TIMING.activationMonth);
    }

    @Test
    void paymentDistressPatterns_intensityGrowth_shouldBePositive() {
        for (AnomalyPatterns.PaymentDistressPattern p : AnomalyPatterns.PaymentDistressPattern.values()) {
            assertThat(p.intensityGrowth)
                    .as("Intensity growth for %s should be positive", p)
                    .isPositive();
        }
    }

    @Test
    void missedPayments_shouldHaveHighestIntensityGrowth() {
        assertThat(AnomalyPatterns.PaymentDistressPattern.MISSED_PAYMENTS.intensityGrowth)
                .isGreaterThan(AnomalyPatterns.PaymentDistressPattern.INCREASING_PARTIALS.intensityGrowth);
    }

    // -------------------------------------------------------------------------
    // DataQualityPattern
    // -------------------------------------------------------------------------

    @Test
    void dataQualityPatterns_injectionRates_shouldBeVeryLow() {
        for (AnomalyPatterns.DataQualityPattern p : AnomalyPatterns.DataQualityPattern.values()) {
            assertThat(p.injectionRate)
                    .as("Injection rate for %s should be < 1%%", p)
                    .isLessThan(0.01);
        }
    }

    @Test
    void duplicateOrder_shouldHaveHigherInjectionRate_thanCoordinateMismatch() {
        assertThat(AnomalyPatterns.DataQualityPattern.DUPLICATE_ORDER.injectionRate)
                .isGreaterThan(AnomalyPatterns.DataQualityPattern.COORDINATE_MISMATCH.injectionRate);
    }

    @Test
    void allPatternEnums_shouldHaveAtLeastThreeValues() {
        assertThat(AnomalyPatterns.ShrinkagePattern.values()).hasSizeGreaterThanOrEqualTo(3);
        assertThat(AnomalyPatterns.PaymentDistressPattern.values()).hasSizeGreaterThanOrEqualTo(3);
        assertThat(AnomalyPatterns.DataQualityPattern.values()).hasSizeGreaterThanOrEqualTo(3);
    }
}
