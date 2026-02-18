package com.zuqi.ai.routing.domain;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

/**
 * Timefold planning entity representing a single delivery stop (one merchant).
 *
 * Each stop is assigned to a {@link Vehicle} by the solver; the order within
 * the vehicle's list determines the delivery sequence.
 *
 * Blueprint reference: plan.md Section 6.5 - Route Optimization
 */
@PlanningEntity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryStop {

    private UUID stopId;
    private UUID merchantId;
    private String merchantName;
    private Location location;

    private List<UUID> orderIds;
    private double weightKg;
    private double volumeCbm;
    private int priority;               // 1 = highest, 5 = lowest

    /** Optional delivery time window (minutes from midnight). */
    private Integer timeWindowStartMin;
    private Integer timeWindowEndMin;
}
