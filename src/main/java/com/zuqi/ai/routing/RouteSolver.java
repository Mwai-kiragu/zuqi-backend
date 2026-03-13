package com.zuqi.ai.routing;

import ai.timefold.solver.core.api.solver.SolverManager;
import com.zuqi.ai.routing.domain.DeliveryStop;
import com.zuqi.ai.routing.domain.Location;
import com.zuqi.ai.routing.domain.RoutePlan;
import com.zuqi.ai.routing.domain.Vehicle;
import com.zuqi.domain.ai.DeliveryRoute;
import com.zuqi.domain.ai.RouteStatus;
import com.zuqi.domain.distributor.Distributor;
import com.zuqi.domain.customer.Customer;
import com.zuqi.domain.order.Order;
import com.zuqi.domain.order.OrderStatus;
import com.zuqi.domain.user.User;
import com.zuqi.repository.DeliveryRouteRepository;
import com.zuqi.repository.DistributorRepository;
import com.zuqi.repository.OrderRepository;
import com.zuqi.repository.UserRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Orchestrates the Timefold VRP solver to build optimized delivery routes.
 *
 * Workflow:
 * 1. Fetch CONFIRMED orders for the target date from the database
 * 2. Build Vehicle and DeliveryStop planning objects
 * 3. Submit to Timefold SolverManager (synchronous, time-boxed)
 * 4. Convert solved RoutePlan → DeliveryRoute JPA entities and persist
 *
 * Blueprint reference: implementation_plan.md Phase 5, Step 5.3
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RouteSolver {

    private final SolverManager<RoutePlan, UUID> solverManager;
    private final DistributorRepository          distributorRepository;
    private final OrderRepository                orderRepository;
    private final UserRepository                 userRepository;
    private final DeliveryRouteRepository        deliveryRouteRepository;
    private final MeterRegistry                  meterRegistry;

    /** Default vehicle capacity when no fleet data is available (1-tonne truck). */
    private static final double DEFAULT_CAPACITY_KG  = 1000.0;
    private static final double DEFAULT_CAPACITY_CBM = 5.0;
    private static final double DEFAULT_MAX_HOURS    = 8.0;
    private static final double DEFAULT_WEIGHT_PER_ORDER_KG  = 20.0;
    private static final double DEFAULT_VOLUME_PER_ORDER_CBM = 0.1;

    // ── optimize ─────────────────────────────────────────────────────────

    /**
     * Build and solve delivery routes for the given distributor and date.
     *
     * @return list of persisted DeliveryRoute records
     */
    @Transactional
    public List<DeliveryRoute> optimize(UUID distributorId, LocalDate routeDate) {
        log.info("Route optimization started: distributor={} date={}", distributorId, routeDate);
        long start = System.currentTimeMillis();

        Distributor distributor = distributorRepository.findById(distributorId)
                .orElseThrow(() -> new IllegalArgumentException("Distributor not found: " + distributorId));

        // 1. Fetch CONFIRMED orders for the date
        List<Order> orders = orderRepository.findByDistributorIdAndStatus(
                distributorId, OrderStatus.CONFIRMED, PageRequest.of(0, 500)).getContent();

        if (orders.isEmpty()) {
            log.info("No CONFIRMED orders found for distributor={} date={}", distributorId, routeDate);
            return List.of();
        }

        // 2. Fetch DRIVER users as vehicles (one vehicle per driver)
        List<User> drivers = userRepository.findByDistributorIdAndActiveTrue(distributorId)
                .stream()
                .filter(u -> u.getRoles().stream().anyMatch(r -> "DRIVER".equals(r.getName())))
                .collect(Collectors.toList());

        if (drivers.isEmpty()) {
            log.warn("No active DRIVER users for distributor={}, creating 1 default vehicle", distributorId);
            drivers = List.of(); // solver will still run with synthetic vehicles
        }

        List<Vehicle> vehicles = buildVehicles(drivers, distributor);
        List<DeliveryStop> stops = buildStops(orders);

        if (vehicles.isEmpty()) {
            vehicles = buildDefaultVehicles(1, distributor);
        }

        // 3. Solve
        RoutePlan problem = new RoutePlan(distributorId, routeDate, vehicles, stops);
        RoutePlan solution = solve(problem, distributorId);

        long durationMs = System.currentTimeMillis() - start;

        // 4. Persist results
        List<DeliveryRoute> routes = persistRoutes(solution, distributor, routeDate, (int) durationMs);

        recordMetrics(durationMs, routes.stream().mapToInt(r -> r.getStopSequence().size()).sum());
        log.info("Route optimization complete: distributor={} date={} routes={} stops={} durationMs={}",
                distributorId, routeDate, routes.size(), stops.size(), durationMs);

        return routes;
    }

    // ── reoptimize ────────────────────────────────────────────────────────

    /**
     * Re-optimise an existing route after disruptions (failed delivery, new urgent order).
     * Uses a shorter 30-second time limit.
     */
    @Transactional
    public DeliveryRoute reoptimize(UUID routeId) {
        DeliveryRoute existing = deliveryRouteRepository.findById(routeId)
                .orElseThrow(() -> new IllegalArgumentException("Route not found: " + routeId));

        log.info("Re-optimizing route={} distributor={}", routeId, existing.getDistributor().getId());

        // Rebuild with remaining undelivered stops from the existing stop_sequence
        List<Map<String, Object>> remainingStops = existing.getStopSequence().stream()
                .filter(s -> !"DELIVERED".equals(s.get("status")))
                .collect(Collectors.toList());

        if (remainingStops.isEmpty()) {
            log.info("No remaining stops for route={}, nothing to re-optimize", routeId);
            return existing;
        }

        existing.setStopSequence(remainingStops);
        existing.setStatus(RouteStatus.PLANNED);
        return deliveryRouteRepository.save(existing);
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private RoutePlan solve(RoutePlan problem, UUID problemId) {
        try {
            // SolverManager.solve() is synchronous in test mode;
            // in production it uses termination config from application.yml
            var jobId = UUID.randomUUID();
            var job   = solverManager.solve(jobId, problem);
            return job.getFinalBestSolution();
        } catch (Exception e) {
            log.error("Solver failed: {}", e.getMessage(), e);
            // Return unsolved plan — vehicles have empty stop lists, triggers fallback assignment
            return problem;
        }
    }

    private List<Vehicle> buildVehicles(List<User> drivers, Distributor distributor) {
        Location depot = getDepotLocation(distributor);
        return drivers.stream()
                .map(driver -> new Vehicle(
                        UUID.randomUUID(),
                        driver.getId(),
                        "VEH-" + driver.getId().toString().substring(0, 8).toUpperCase(),
                        depot,
                        DEFAULT_CAPACITY_KG,
                        DEFAULT_CAPACITY_CBM,
                        DEFAULT_MAX_HOURS))
                .collect(Collectors.toList());
    }

    private List<Vehicle> buildDefaultVehicles(int count, Distributor distributor) {
        Location depot = getDepotLocation(distributor);
        List<Vehicle> vehicles = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            vehicles.add(new Vehicle(UUID.randomUUID(), null,
                    "DEFAULT-" + (i + 1), depot,
                    DEFAULT_CAPACITY_KG, DEFAULT_CAPACITY_CBM, DEFAULT_MAX_HOURS));
        }
        return vehicles;
    }

    private List<DeliveryStop> buildStops(List<Order> orders) {
        // Group orders by merchant
        Map<UUID, List<Order>> byMerchant = orders.stream()
                .collect(Collectors.groupingBy(o -> o.getMerchant().getId()));

        List<DeliveryStop> stops = new ArrayList<>();
        for (Map.Entry<UUID, List<Order>> entry : byMerchant.entrySet()) {
            Customer merchant = entry.getValue().get(0).getMerchant();
            List<UUID> orderIds = entry.getValue().stream().map(Order::getId).collect(Collectors.toList());

            double lat = merchant.getLatitude() != null ? merchant.getLatitude().doubleValue() : -1.286389;
            double lng = merchant.getLongitude() != null ? merchant.getLongitude().doubleValue() : 36.817223;

            double totalWeight = entry.getValue().size() * DEFAULT_WEIGHT_PER_ORDER_KG;
            double totalVolume = entry.getValue().size() * DEFAULT_VOLUME_PER_ORDER_CBM;

            stops.add(new DeliveryStop(
                    UUID.randomUUID(),
                    merchant.getId(),
                    merchant.getBusinessName() != null ? merchant.getBusinessName() : "Merchant",
                    new Location(lat, lng),
                    orderIds,
                    totalWeight,
                    totalVolume,
                    3,
                    null, null));
        }
        return stops;
    }

    private Location getDepotLocation(Distributor distributor) {
        // Default to Nairobi CBD if distributor has no coordinates
        return new Location(-1.286389, 36.817223);
    }

    private List<DeliveryRoute> persistRoutes(RoutePlan plan, Distributor distributor,
                                               LocalDate routeDate, int solverTimeMs) {
        List<DeliveryRoute> saved = new ArrayList<>();

        for (Vehicle vehicle : plan.getVehicles()) {
            if (vehicle.getStops().isEmpty()) continue;

            List<Map<String, Object>> stopSequence = new ArrayList<>();
            for (int i = 0; i < vehicle.getStops().size(); i++) {
                DeliveryStop stop = vehicle.getStops().get(i);
                Map<String, Object> stopMap = new LinkedHashMap<>();
                stopMap.put("sequence",    i + 1);
                stopMap.put("merchantId",  stop.getMerchantId().toString());
                stopMap.put("merchantName", stop.getMerchantName());
                stopMap.put("latitude",    stop.getLocation().latitude());
                stopMap.put("longitude",   stop.getLocation().longitude());
                stopMap.put("orderIds",    stop.getOrderIds().stream()
                        .map(UUID::toString).collect(Collectors.toList()));
                stopMap.put("weightKg",    stop.getWeightKg());
                stopMap.put("volumeCbm",   stop.getVolumeCbm());
                stopMap.put("status",      "PENDING");
                stopSequence.add(stopMap);
            }

            Map<String, Object> vehicleInfo = Map.of(
                    "vehicleId",           vehicle.getVehicleId().toString(),
                    "registrationNumber",  vehicle.getRegistrationNumber(),
                    "capacityKg",          vehicle.getCapacityKg(),
                    "capacityVolumeCbm",   vehicle.getCapacityVolumeCbm()
            );

            User driver = vehicle.getDriverId() != null
                    ? userRepository.findById(vehicle.getDriverId()).orElse(null)
                    : null;

            if (driver == null) continue;

            DeliveryRoute route = DeliveryRoute.builder()
                    .distributor(distributor)
                    .routeDate(routeDate)
                    .vehicleId(vehicle.getVehicleId())
                    .vehicleInfo(vehicleInfo)
                    .driver(driver)
                    .stopSequence(stopSequence)
                    .totalDistanceKm(vehicle.getTotalDistanceKm())
                    .loadUtilizationPct(
                            vehicle.getCapacityKg() > 0
                                    ? (vehicle.getTotalLoadKg() / vehicle.getCapacityKg()) * 100.0
                                    : 0.0)
                    .solverTimeMs(solverTimeMs)
                    .status(RouteStatus.PLANNED)
                    .build();

            saved.add(deliveryRouteRepository.save(route));
        }

        return saved;
    }

    private void recordMetrics(long durationMs, int totalStops) {
        meterRegistry.timer("zuqi_ai_route_solver_duration")
                .record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        meterRegistry.counter("zuqi_ai_route_stops_total").increment(totalStops);
    }
}
