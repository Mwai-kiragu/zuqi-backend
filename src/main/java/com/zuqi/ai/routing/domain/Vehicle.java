package com.zuqi.ai.routing.domain;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.variable.PlanningListVariable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Timefold planning entity representing a delivery vehicle.
 *
 * The list variable {@code stops} is the planning variable —
 * Timefold decides which stops to assign to each vehicle and in what order.
 *
 * Blueprint reference: plan.md Section 6.5 - Route Optimization
 */
@PlanningEntity
@Getter
@Setter
@NoArgsConstructor
public class Vehicle {

    private UUID vehicleId;
    private UUID driverId;
    private String registrationNumber;
    private Location startLocation;     // Warehouse / depot location

    private double capacityKg;
    private double capacityVolumeCbm;
    private double maxDrivingHours;

    @PlanningListVariable
    private List<DeliveryStop> stops = new ArrayList<>();

    public Vehicle(UUID vehicleId, UUID driverId, String registrationNumber,
                   Location startLocation, double capacityKg,
                   double capacityVolumeCbm, double maxDrivingHours) {
        this.vehicleId          = vehicleId;
        this.driverId           = driverId;
        this.registrationNumber = registrationNumber;
        this.startLocation      = startLocation;
        this.capacityKg         = capacityKg;
        this.capacityVolumeCbm  = capacityVolumeCbm;
        this.maxDrivingHours    = maxDrivingHours;
    }

    /** Total weight of all assigned stops in kg. */
    public double getTotalLoadKg() {
        return stops.stream().mapToDouble(DeliveryStop::getWeightKg).sum();
    }

    /** Total volume of all assigned stops in cubic meters. */
    public double getTotalLoadVolumeCbm() {
        return stops.stream().mapToDouble(DeliveryStop::getVolumeCbm).sum();
    }

    /** Estimated total route distance in km using Haversine. */
    public double getTotalDistanceKm() {
        if (stops.isEmpty()) return 0.0;
        double total = startLocation.haversineDistanceKm(stops.get(0).getLocation());
        for (int i = 1; i < stops.size(); i++) {
            total += stops.get(i - 1).getLocation().haversineDistanceKm(stops.get(i).getLocation());
        }
        return total;
    }
}
