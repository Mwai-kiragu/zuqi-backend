package com.zuqi.ai.routing;

import com.zuqi.ai.routing.domain.DeliveryRouteConstraintProvider;
import com.zuqi.ai.routing.domain.DeliveryStop;
import com.zuqi.ai.routing.domain.Location;
import com.zuqi.ai.routing.domain.Vehicle;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DeliveryRouteConstraintProvider} constraint logic.
 *
 * Because invoking Timefold's ConstraintFactory API outside the solver
 * context is impractical in a unit test, these tests validate the
 * {@link Vehicle} helper methods that the constraints directly delegate to:
 *
 * <ul>
 *   <li>{@code getTotalLoadKg()}       — weight capacity filter</li>
 *   <li>{@code getTotalLoadVolumeCbm()} — volume capacity filter</li>
 *   <li>{@code getTotalDistanceKm()}    — soft-constraint penalty value</li>
 * </ul>
 *
 * Each test documents the constraint name alongside the vehicle computation,
 * making it clear which constraint is being exercised.
 */
class DeliveryRouteConstraintProviderTest {

    // ── Constraint: "Vehicle weight capacity exceeded" ────────────────────

    @Test
    void weightCapacity_whenTotalLoadUnderCapacity_filterConditionIsFalse() {
        Vehicle vehicle = vehicleWith(1000.0, 5.0);
        vehicle.setStops(List.of(stop(400.0, 1.0)));

        assertThat(vehicle.getTotalLoadKg()).isEqualTo(400.0);
        // Hard constraint filter: v.getTotalLoadKg() > v.getCapacityKg()
        assertThat(vehicle.getTotalLoadKg() > vehicle.getCapacityKg()).isFalse();
    }

    @Test
    void weightCapacity_whenTotalLoadOverCapacity_filterConditionIsTrue() {
        Vehicle vehicle = vehicleWith(1000.0, 5.0);
        vehicle.setStops(List.of(stop(600.0, 1.0), stop(600.0, 1.0)));

        assertThat(vehicle.getTotalLoadKg()).isEqualTo(1200.0);
        // Hard constraint triggers
        assertThat(vehicle.getTotalLoadKg() > vehicle.getCapacityKg()).isTrue();
        // Penalty = Math.round((overload) * 1000)
        long penalty = Math.round((vehicle.getTotalLoadKg() - vehicle.getCapacityKg()) * 1000);
        assertThat(penalty).isEqualTo(200_000L);
    }

    @Test
    void weightCapacity_emptyVehicle_zeroLoad() {
        Vehicle vehicle = vehicleWith(1000.0, 5.0);

        assertThat(vehicle.getTotalLoadKg()).isEqualTo(0.0);
        assertThat(vehicle.getTotalLoadKg() > vehicle.getCapacityKg()).isFalse();
    }

    // ── Constraint: "Vehicle volume capacity exceeded" ────────────────────

    @Test
    void volumeCapacity_whenVolumeUnderCapacity_filterConditionIsFalse() {
        Vehicle vehicle = vehicleWith(1000.0, 5.0);
        vehicle.setStops(List.of(stop(200.0, 2.0)));

        assertThat(vehicle.getTotalLoadVolumeCbm()).isEqualTo(2.0);
        // Hard constraint filter: v.getTotalLoadVolumeCbm() > v.getCapacityVolumeCbm()
        assertThat(vehicle.getTotalLoadVolumeCbm() > vehicle.getCapacityVolumeCbm()).isFalse();
    }

    @Test
    void volumeCapacity_whenVolumeOverCapacity_filterConditionIsTrue() {
        Vehicle vehicle = vehicleWith(1000.0, 2.0); // capacity only 2 cbm
        vehicle.setStops(List.of(stop(100.0, 1.5), stop(100.0, 1.5)));

        assertThat(vehicle.getTotalLoadVolumeCbm()).isEqualTo(3.0);
        // Hard constraint triggers
        assertThat(vehicle.getTotalLoadVolumeCbm() > vehicle.getCapacityVolumeCbm()).isTrue();
        long penalty = Math.round((vehicle.getTotalLoadVolumeCbm() - vehicle.getCapacityVolumeCbm()) * 1000);
        assertThat(penalty).isEqualTo(1_000L);
    }

    // ── Constraint: "Minimize total route distance" (soft) ────────────────

    @Test
    void distanceConstraint_emptyVehicle_filterIsFalse_distanceIsZero() {
        Vehicle vehicle = vehicleWith(1000.0, 5.0);

        // Soft constraint filter: !v.getStops().isEmpty()
        assertThat(vehicle.getStops().isEmpty()).isTrue();
        assertThat(vehicle.getTotalDistanceKm()).isEqualTo(0.0);
    }

    @Test
    void distanceConstraint_vehicleWithStop_filterIsTrue_penaltyIsPositive() {
        Location depot = new Location(-1.2864, 36.8172); // Nairobi CBD
        Vehicle vehicle = new Vehicle(UUID.randomUUID(), null, "VEH-1", depot, 1000.0, 5.0, 8.0);

        // Stop roughly 1 km north of the depot
        DeliveryStop stop = stop(100.0, 1.0);
        stop.setLocation(new Location(-1.277, 36.817));
        vehicle.setStops(List.of(stop));

        // Soft constraint filter passes
        assertThat(vehicle.getStops().isEmpty()).isFalse();
        double distanceKm = vehicle.getTotalDistanceKm();
        assertThat(distanceKm).isGreaterThan(0.0);
        // Penalty in metres precision
        assertThat(Math.round(distanceKm * 1000)).isGreaterThan(0L);
    }

    // ── Provider smoke test ───────────────────────────────────────────────

    @Test
    void constraintProvider_canBeInstantiated() {
        DeliveryRouteConstraintProvider provider = new DeliveryRouteConstraintProvider();
        assertThat(provider).isNotNull();
    }

    // ── helpers ───────────────────────────────────────────────────────────

    /** Creates a Vehicle with Nairobi CBD as depot. */
    private Vehicle vehicleWith(double capacityKg, double capacityVolumeCbm) {
        Location depot = new Location(-1.2864, 36.8172);
        return new Vehicle(UUID.randomUUID(), null, "VEH-TEST",
                depot, capacityKg, capacityVolumeCbm, 8.0);
    }

    /** Creates a DeliveryStop with given weight/volume at a fixed location. */
    private DeliveryStop stop(double weightKg, double volumeCbm) {
        DeliveryStop s = new DeliveryStop();
        s.setStopId(UUID.randomUUID());
        s.setWeightKg(weightKg);
        s.setVolumeCbm(volumeCbm);
        s.setLocation(new Location(-1.290, 36.820));
        return s;
    }
}
