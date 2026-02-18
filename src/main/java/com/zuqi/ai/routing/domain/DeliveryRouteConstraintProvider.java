package com.zuqi.ai.routing.domain;

import ai.timefold.solver.core.api.score.buildin.hardsoftlong.HardSoftLongScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;

/**
 * Timefold constraint definitions for the Zuqi delivery VRP.
 *
 * Hard constraints (must not be violated):
 * 1. Vehicle weight capacity — total load must not exceed capacityKg
 * 2. Vehicle volume capacity — total volume must not exceed capacityVolumeCbm
 *
 * Soft constraints (penalise but allow):
 * 3. Minimize total route distance — penalise by distance in metres (integer precision)
 *
 * Blueprint reference: plan.md Section 6.5 - Route Optimization
 */
public class DeliveryRouteConstraintProvider implements ConstraintProvider {

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
        return new Constraint[]{
                vehicleWeightCapacity(factory),
                vehicleVolumeCapacity(factory),
                minimizeTotalDistance(factory)
        };
    }

    // ── Hard: weight capacity ─────────────────────────────────────────────

    Constraint vehicleWeightCapacity(ConstraintFactory factory) {
        return factory.forEach(Vehicle.class)
                .filter(v -> v.getTotalLoadKg() > v.getCapacityKg())
                .penalizeLong(HardSoftLongScore.ONE_HARD,
                        v -> Math.round((v.getTotalLoadKg() - v.getCapacityKg()) * 1000))
                .asConstraint("Vehicle weight capacity exceeded");
    }

    // ── Hard: volume capacity ─────────────────────────────────────────────

    Constraint vehicleVolumeCapacity(ConstraintFactory factory) {
        return factory.forEach(Vehicle.class)
                .filter(v -> v.getTotalLoadVolumeCbm() > v.getCapacityVolumeCbm())
                .penalizeLong(HardSoftLongScore.ONE_HARD,
                        v -> Math.round((v.getTotalLoadVolumeCbm() - v.getCapacityVolumeCbm()) * 1000))
                .asConstraint("Vehicle volume capacity exceeded");
    }

    // ── Soft: minimize total distance ─────────────────────────────────────

    Constraint minimizeTotalDistance(ConstraintFactory factory) {
        return factory.forEach(Vehicle.class)
                .filter(v -> !v.getStops().isEmpty())
                .penalizeLong(HardSoftLongScore.ONE_SOFT,
                        v -> Math.round(v.getTotalDistanceKm() * 1000))  // metres precision
                .asConstraint("Minimize total route distance");
    }
}
