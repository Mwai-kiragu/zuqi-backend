package com.zuqi.ai.procurement;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for PriceTrendAnalyzer math helpers.
 * Integration with repos is covered by job-level tests.
 */
class PriceTrendAnalyzerTest {

    // Instantiate without Spring — math methods are package-private
    private final PriceTrendAnalyzer analyzer = new PriceTrendAnalyzer(
            null, null, null, null, null);

    private static PriceTrendAnalyzer.PricePoint point(int dayOffset, double price) {
        return new PriceTrendAnalyzer.PricePoint(
                LocalDateTime.now().minusDays(dayOffset), price);
    }

    // ── computeSlope ─────────────────────────────────────────────────────────

    @Test
    void slope_increasingPrices_isPositive() {
        List<PriceTrendAnalyzer.PricePoint> points = List.of(
                point(6, 100), point(5, 110), point(4, 120),
                point(3, 130), point(2, 140), point(1, 150));

        double slope = analyzer.computeSlope(points);

        assertThat(slope).isGreaterThan(0);
    }

    @Test
    void slope_decreasingPrices_isNegative() {
        List<PriceTrendAnalyzer.PricePoint> points = List.of(
                point(5, 200), point(4, 180), point(3, 160),
                point(2, 140), point(1, 120));

        double slope = analyzer.computeSlope(points);

        assertThat(slope).isLessThan(0);
    }

    @Test
    void slope_flatPrices_isZero() {
        List<PriceTrendAnalyzer.PricePoint> points = List.of(
                point(4, 100), point(3, 100), point(2, 100), point(1, 100));

        double slope = analyzer.computeSlope(points);

        assertThat(slope).isCloseTo(0.0, within(0.001));
    }

    @Test
    void slope_singlePoint_isZero() {
        List<PriceTrendAnalyzer.PricePoint> points = List.of(point(0, 100));

        double slope = analyzer.computeSlope(points);

        assertThat(slope).isEqualTo(0.0);
    }

    // ── computeStddev ────────────────────────────────────────────────────────

    @Test
    void stddev_uniformPrices_isZero() {
        List<PriceTrendAnalyzer.PricePoint> points = List.of(
                point(3, 100), point(2, 100), point(1, 100));

        double stddev = analyzer.computeStddev(points, 100.0);

        assertThat(stddev).isCloseTo(0.0, within(0.001));
    }

    @Test
    void stddev_varyingPrices_isPositive() {
        List<PriceTrendAnalyzer.PricePoint> points = List.of(
                point(4, 80), point(3, 100), point(2, 120), point(1, 140));
        double mean = 110.0;

        double stddev = analyzer.computeStddev(points, mean);

        assertThat(stddev).isGreaterThan(0.0);
    }

    @Test
    void stddev_twoPoints_knownValue() {
        // prices: 80, 120; mean = 100; variance = ((80-100)² + (120-100)²)/2 = 400; stddev = 20
        List<PriceTrendAnalyzer.PricePoint> points = List.of(
                point(1, 80), point(0, 120));

        double stddev = analyzer.computeStddev(points, 100.0);

        assertThat(stddev).isCloseTo(20.0, within(0.01));
    }

    // ── Direction thresholds (smoke test via slope logic) ───────────────────

    @Test
    void slope_linearIncrease10PerStep_matches() {
        // y = 10x: slope should be 10
        List<PriceTrendAnalyzer.PricePoint> points = List.of(
                point(4, 0), point(3, 10), point(2, 20), point(1, 30));

        double slope = analyzer.computeSlope(points);

        assertThat(slope).isCloseTo(10.0, within(0.1));
    }
}
