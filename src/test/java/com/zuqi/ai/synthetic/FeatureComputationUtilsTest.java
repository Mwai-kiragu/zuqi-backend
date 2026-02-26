package com.zuqi.ai.synthetic;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class FeatureComputationUtilsTest {

    // ── computeLinearRegressionSlope ──────────────────────────────────────

    @Test
    void slope_perfectPositiveLine_returnsOne() {
        double[] x = {0, 1, 2, 3, 4};
        double[] y = {0, 1, 2, 3, 4};
        assertThat(FeatureComputationUtils.computeLinearRegressionSlope(x, y))
                .isCloseTo(1.0, within(1e-9));
    }

    @Test
    void slope_perfectNegativeLine_returnsNegativeOne() {
        double[] x = {0, 1, 2, 3};
        double[] y = {4, 3, 2, 1};
        assertThat(FeatureComputationUtils.computeLinearRegressionSlope(x, y))
                .isCloseTo(-1.0, within(1e-9));
    }

    @Test
    void slope_flatLine_returnsZero() {
        double[] x = {0, 1, 2, 3};
        double[] y = {5, 5, 5, 5};
        assertThat(FeatureComputationUtils.computeLinearRegressionSlope(x, y))
                .isEqualTo(0.0);
    }

    @Test
    void slope_singlePoint_returnsZero() {
        double[] x = {1};
        double[] y = {2};
        assertThat(FeatureComputationUtils.computeLinearRegressionSlope(x, y))
                .isEqualTo(0.0);
    }

    @Test
    void slope_emptyArray_returnsZero() {
        assertThat(FeatureComputationUtils.computeLinearRegressionSlope(new double[]{}, new double[]{}))
                .isEqualTo(0.0);
    }

    // ── computeStandardDeviation ──────────────────────────────────────────

    @Test
    void stddev_identicalValues_returnsZero() {
        double[] values = {5, 5, 5, 5};
        assertThat(FeatureComputationUtils.computeStandardDeviation(values))
                .isEqualTo(0.0);
    }

    @Test
    void stddev_knownValues_returnsExpected() {
        // Population stddev of {2, 4, 4, 4, 5, 5, 7, 9} = 2.0
        double[] values = {2, 4, 4, 4, 5, 5, 7, 9};
        assertThat(FeatureComputationUtils.computeStandardDeviation(values))
                .isCloseTo(2.0, within(1e-9));
    }

    @Test
    void stddev_singleValue_returnsZero() {
        assertThat(FeatureComputationUtils.computeStandardDeviation(new double[]{42}))
                .isEqualTo(0.0);
    }

    @Test
    void stddev_emptyArray_returnsZero() {
        assertThat(FeatureComputationUtils.computeStandardDeviation(new double[]{}))
                .isEqualTo(0.0);
    }

    // ── computePercentage ─────────────────────────────────────────────────

    @Test
    void percentage_halfOfTotal_returns50() {
        assertThat(FeatureComputationUtils.computePercentage(5, 10)).isEqualTo(50.0);
    }

    @Test
    void percentage_zeroNumerator_returnsZero() {
        assertThat(FeatureComputationUtils.computePercentage(0, 100)).isEqualTo(0.0);
    }

    @Test
    void percentage_zeroDenominator_returnsZero() {
        assertThat(FeatureComputationUtils.computePercentage(5, 0)).isEqualTo(0.0);
    }

    @Test
    void percentage_allOfTotal_returns100() {
        assertThat(FeatureComputationUtils.computePercentage(7, 7)).isEqualTo(100.0);
    }

    // ── computeConsumptionTrend ───────────────────────────────────────────

    @Test
    void consumptionTrend_bothZero_returnsStable() {
        assertThat(FeatureComputationUtils.computeConsumptionTrend(BigDecimal.ZERO, BigDecimal.ZERO))
                .isEqualTo("STABLE");
    }

    @Test
    void consumptionTrend_rate7dMuchHigher_returnsIncreasing() {
        // diff = (50 - 20) / 20 = 1.50 > 0.20
        assertThat(FeatureComputationUtils.computeConsumptionTrend(
                BigDecimal.valueOf(50), BigDecimal.valueOf(20)))
                .isEqualTo("INCREASING");
    }

    @Test
    void consumptionTrend_rate7dMuchLower_returnsDecreasing() {
        // diff = (5 - 20) / 20 = -0.75 < -0.20
        assertThat(FeatureComputationUtils.computeConsumptionTrend(
                BigDecimal.valueOf(5), BigDecimal.valueOf(20)))
                .isEqualTo("DECREASING");
    }

    @Test
    void consumptionTrend_smallDifference_returnsStable() {
        // diff = (21 - 20) / 20 = 0.05 — within ±0.20 threshold
        assertThat(FeatureComputationUtils.computeConsumptionTrend(
                BigDecimal.valueOf(21), BigDecimal.valueOf(20)))
                .isEqualTo("STABLE");
    }

    // ── computeTrendDirection ─────────────────────────────────────────────

    @Test
    void trendDirection_increasingValues_returnsIncreasing() {
        assertThat(FeatureComputationUtils.computeTrendDirection(
                List.of(1.0, 2.0, 3.0, 4.0, 5.0), 0.5))
                .isEqualTo("INCREASING");
    }

    @Test
    void trendDirection_decreasingValues_returnsDecreasing() {
        assertThat(FeatureComputationUtils.computeTrendDirection(
                List.of(5.0, 4.0, 3.0, 2.0, 1.0), 0.5))
                .isEqualTo("DECREASING");
    }

    @Test
    void trendDirection_flatValues_returnsStable() {
        assertThat(FeatureComputationUtils.computeTrendDirection(
                List.of(10.0, 10.0, 10.0, 10.0), 0.5))
                .isEqualTo("STABLE");
    }

    @Test
    void trendDirection_singleValue_returnsStable() {
        assertThat(FeatureComputationUtils.computeTrendDirection(List.of(5.0), 0.5))
                .isEqualTo("STABLE");
    }

    @Test
    void trendDirection_emptyList_returnsStable() {
        assertThat(FeatureComputationUtils.computeTrendDirection(List.of(), 0.5))
                .isEqualTo("STABLE");
    }
}
