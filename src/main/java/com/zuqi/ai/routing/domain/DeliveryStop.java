package com.zuqi.ai.routing.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

/**
 * A single delivery stop (one merchant) used as a planning value in the VRP.
 *
 * Stops are the value range for {@link Vehicle#stops} (@PlanningListVariable).
 * The solver assigns stops to vehicles and determines their order.
 *
 * Blueprint reference: plan.md Section 6.5 - Route Optimization
 */
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
