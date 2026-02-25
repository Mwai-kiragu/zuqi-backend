package com.zuqi.ai.routing;

import com.zuqi.ai.routing.domain.Location;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Computes travel distance (km) and estimated duration (minutes) between locations.
 *
 * Primary: Haversine great-circle formula with a road-factor correction (1.35×).
 * This gives accurate enough estimates for Kenya's road network without requiring
 * a Kenya OSM file at startup. GraphHopper can be wired in later when the OSM
 * file is available by overriding {@link #getRoadDistanceKm}.
 *
 * Redis caching (TTL 30 days) prevents re-computing frequently queried pairs.
 *
 * Blueprint reference: implementation_plan.md Phase 5, Step 5.1
 */
@Service
@Slf4j
public class DistanceMatrixService {

    /**
     * Road correction factor applied to straight-line Haversine distance.
     * 1.35 is a typical empirical value for sub-Saharan urban/peri-urban roads.
     */
    private static final double ROAD_FACTOR = 1.35;

    /**
     * Assumed average speed in km/h (typical matatu/truck speed in Kenya).
     * Used to derive duration from distance.
     */
    private static final double AVG_SPEED_KMH = 40.0;

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Get road distance in km between two locations.
     * Result is cached by (lat1,lng1,lat2,lng2) tuple.
     */
    @Cacheable(value = "distanceMatrix",
            key = "#from.latitude() + ',' + #from.longitude() + ',' + #to.latitude() + ',' + #to.longitude()")
    public double getDistanceKm(Location from, Location to) {
        return getRoadDistanceKm(from, to);
    }

    /**
     * Get estimated travel duration in minutes between two locations.
     */
    @Cacheable(value = "distanceMatrix",
            key = "'dur:' + #from.latitude() + ',' + #from.longitude() + ',' + #to.latitude() + ',' + #to.longitude()")
    public double getDurationMinutes(Location from, Location to) {
        double distanceKm = getRoadDistanceKm(from, to);
        return (distanceKm / AVG_SPEED_KMH) * 60.0;
    }

    /**
     * Compute a full N×N distance matrix for a list of locations.
     * Returns distances[i][j] = km from location i to location j.
     */
    public double[][] computeMatrix(List<Location> locations) {
        int n = locations.size();
        double[][] matrix = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i != j) {
                    matrix[i][j] = getRoadDistanceKm(locations.get(i), locations.get(j));
                }
            }
        }
        log.debug("Computed {}×{} distance matrix", n, n);
        return matrix;
    }

    // ── Core computation ──────────────────────────────────────────────────

    /**
     * Returns road distance in km.
     * Subclasses or future versions can override this to use GraphHopper.
     */
    protected double getRoadDistanceKm(Location from, Location to) {
        double straightLine = from.haversineDistanceKm(to);
        return straightLine * ROAD_FACTOR;
    }
}
