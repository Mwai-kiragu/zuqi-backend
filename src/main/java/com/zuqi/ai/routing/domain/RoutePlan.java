package com.zuqi.ai.routing.domain;

import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.solution.ProblemFactCollectionProperty;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.score.buildin.hardsoftlong.HardSoftLongScore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Timefold planning solution for the Vehicle Routing Problem (VRP).
 *
 * The solver assigns {@link DeliveryStop}s to {@link Vehicle}s and
 * determines the optimal stop sequence within each vehicle route.
 *
 * Score: HardSoftLong
 * - Hard: capacity violations (weight, volume), unassigned stops
 * - Soft: minimize total distance
 *
 * Blueprint reference: plan.md Section 6.5 - Route Optimization
 */
@PlanningSolution
@Getter
@Setter
@NoArgsConstructor
public class RoutePlan {

    private UUID distributorId;
    private LocalDate routeDate;

    @ProblemFactCollectionProperty
    @ValueRangeProvider
    private List<DeliveryStop> stops;

    @PlanningEntityCollectionProperty
    private List<Vehicle> vehicles;

    @PlanningScore
    private HardSoftLongScore score;

    public RoutePlan(UUID distributorId, LocalDate routeDate,
                     List<Vehicle> vehicles, List<DeliveryStop> stops) {
        this.distributorId = distributorId;
        this.routeDate     = routeDate;
        this.vehicles      = vehicles;
        this.stops         = stops;
    }

    /** Total planned distance across all vehicles in km. */
    public double getTotalDistanceKm() {
        if (vehicles == null) return 0.0;
        return vehicles.stream().mapToDouble(Vehicle::getTotalDistanceKm).sum();
    }

    /** Total stops assigned across all vehicles. */
    public int getTotalAssignedStops() {
        if (vehicles == null) return 0;
        return vehicles.stream().mapToInt(v -> v.getStops().size()).sum();
    }
}
