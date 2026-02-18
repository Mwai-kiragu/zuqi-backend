package com.zuqi.ai.routing;

import com.zuqi.ai.routing.domain.Location;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for {@link DistanceMatrixService}.
 *
 * Instantiated with {@code new DistanceMatrixService()} — no Spring context,
 * no cache infrastructure needed.  The {@code @Cacheable} annotations are
 * inactive outside of a Spring container, so each call exercises the real
 * computation path.
 *
 * Formula under test:
 *   roadDistance  = haversine(from, to) × 1.35  (ROAD_FACTOR)
 *   durationMin   = roadDistance / 40.0 × 60.0  (AVG_SPEED_KMH)
 */
class DistanceMatrixServiceTest {

    private static final double ROAD_FACTOR   = 1.35;
    private static final double AVG_SPEED_KMH = 40.0;

    // ── Reference locations ────────────────────────────────────────────────
    private static final Location NAIROBI_CBD = new Location(-1.2864, 36.8172);
    private static final Location WESTLANDS   = new Location(-1.2673, 36.8065);
    private static final Location MOMBASA     = new Location(-4.043,  39.668);

    private DistanceMatrixService service;

    @BeforeEach
    void setUp() {
        // Pure instantiation — no Spring required
        service = new DistanceMatrixService();
    }

    // ── getDistanceKm ─────────────────────────────────────────────────────

    @Test
    void getDistanceKm_sameLocation_returnsZero() {
        double dist = service.getDistanceKm(NAIROBI_CBD, NAIROBI_CBD);
        assertThat(dist).isEqualTo(0.0, within(1e-9));
    }

    @Test
    void getDistanceKm_appliesRoadFactor_nairobiToWestlands() {
        double haversine = NAIROBI_CBD.haversineDistanceKm(WESTLANDS);
        double expected  = haversine * ROAD_FACTOR;

        double actual = service.getDistanceKm(NAIROBI_CBD, WESTLANDS);

        assertThat(actual).isEqualTo(expected, within(1e-6));
    }

    @Test
    void getDistanceKm_appliesRoadFactor_nairobiToMombasa() {
        double haversine = NAIROBI_CBD.haversineDistanceKm(MOMBASA);
        double expected  = haversine * ROAD_FACTOR;

        double actual = service.getDistanceKm(NAIROBI_CBD, MOMBASA);

        assertThat(actual).isEqualTo(expected, within(1e-6));
    }

    @Test
    void getDistanceKm_roadDistanceIsGreaterThanHaversine_forDifferentLocations() {
        double haversine = NAIROBI_CBD.haversineDistanceKm(MOMBASA);
        double road      = service.getDistanceKm(NAIROBI_CBD, MOMBASA);
        assertThat(road).isGreaterThan(haversine);
    }

    @Test
    void getDistanceKm_isSymmetric() {
        double forward  = service.getDistanceKm(NAIROBI_CBD, WESTLANDS);
        double backward = service.getDistanceKm(WESTLANDS, NAIROBI_CBD);
        assertThat(forward).isEqualTo(backward, within(1e-6));
    }

    // ── getDurationMinutes ─────────────────────────────────────────────────

    @Test
    void getDurationMinutes_sameLocation_returnsZero() {
        double duration = service.getDurationMinutes(NAIROBI_CBD, NAIROBI_CBD);
        assertThat(duration).isEqualTo(0.0, within(1e-9));
    }

    @Test
    void getDurationMinutes_derivedFromRoadDistance() {
        double roadKm   = service.getDistanceKm(NAIROBI_CBD, WESTLANDS);
        double expected = (roadKm / AVG_SPEED_KMH) * 60.0;

        double actual = service.getDurationMinutes(NAIROBI_CBD, WESTLANDS);

        assertThat(actual).isEqualTo(expected, within(1e-6));
    }

    @Test
    void getDurationMinutes_isPositiveForDifferentLocations() {
        double duration = service.getDurationMinutes(NAIROBI_CBD, MOMBASA);
        assertThat(duration).isGreaterThan(0.0);
    }

    @Test
    void getDurationMinutes_nairobiToMombasa_roughlyReasonableHours() {
        // Road distance ≈ 410 km × 1.35 ≈ 553 km; at 40 km/h ≈ 830 minutes (~13.8 hours)
        double duration = service.getDurationMinutes(NAIROBI_CBD, MOMBASA);
        assertThat(duration).isBetween(600.0, 1100.0);
    }

    // ── computeMatrix ─────────────────────────────────────────────────────

    @Test
    void computeMatrix_emptyList_returnsZeroByZeroMatrix() {
        double[][] matrix = service.computeMatrix(List.of());
        assertThat(matrix).hasNumberOfRows(0);
    }

    @Test
    void computeMatrix_singleLocation_returnsOneByOneMatrixWithZero() {
        double[][] matrix = service.computeMatrix(List.of(NAIROBI_CBD));
        assertThat(matrix).hasNumberOfRows(1);
        assertThat(matrix[0]).hasSize(1);
        assertThat(matrix[0][0]).isEqualTo(0.0, within(1e-9));
    }

    @Test
    void computeMatrix_threeLocations_returnsThreeByThreeMatrix() {
        List<Location> locations = List.of(NAIROBI_CBD, WESTLANDS, MOMBASA);
        double[][] matrix = service.computeMatrix(locations);

        assertThat(matrix).hasNumberOfRows(3);
        for (double[] row : matrix) {
            assertThat(row).hasSize(3);
        }
    }

    @Test
    void computeMatrix_diagonalIsZero_threeLocations() {
        List<Location> locations = List.of(NAIROBI_CBD, WESTLANDS, MOMBASA);
        double[][] matrix = service.computeMatrix(locations);

        for (int i = 0; i < 3; i++) {
            assertThat(matrix[i][i])
                    .as("Diagonal element [%d][%d] should be 0.0", i, i)
                    .isEqualTo(0.0, within(1e-9));
        }
    }

    @Test
    void computeMatrix_isSymmetric_threeLocations() {
        List<Location> locations = List.of(NAIROBI_CBD, WESTLANDS, MOMBASA);
        double[][] matrix = service.computeMatrix(locations);

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                assertThat(matrix[i][j])
                        .as("matrix[%d][%d] should equal matrix[%d][%d]", i, j, j, i)
                        .isEqualTo(matrix[j][i], within(1e-6));
            }
        }
    }

    @Test
    void computeMatrix_offDiagonalMatchesGetDistanceKm() {
        List<Location> locations = List.of(NAIROBI_CBD, MOMBASA);
        double[][] matrix = service.computeMatrix(locations);

        double expected = service.getDistanceKm(NAIROBI_CBD, MOMBASA);
        assertThat(matrix[0][1]).isEqualTo(expected, within(1e-6));
        assertThat(matrix[1][0]).isEqualTo(expected, within(1e-6));
    }

    @Test
    void computeMatrix_allNonNegativeValues() {
        List<Location> locations = List.of(NAIROBI_CBD, WESTLANDS, MOMBASA);
        double[][] matrix = service.computeMatrix(locations);

        for (int i = 0; i < matrix.length; i++) {
            for (int j = 0; j < matrix[i].length; j++) {
                assertThat(matrix[i][j])
                        .as("matrix[%d][%d] should be non-negative", i, j)
                        .isGreaterThanOrEqualTo(0.0);
            }
        }
    }
}
