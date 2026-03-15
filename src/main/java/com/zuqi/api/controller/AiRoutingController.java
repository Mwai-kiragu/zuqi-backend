package com.zuqi.api.controller;

import com.zuqi.ai.routing.RouteSolver;
import com.zuqi.api.dto.ApiResponse;
import com.zuqi.domain.ai.DeliveryRoute;
import com.zuqi.domain.ai.RouteStatus;
import com.zuqi.repository.DeliveryRouteRepository;
import com.zuqi.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST API for AI-powered route optimization.
 *
 * Authorization is handled by Casbin policy.csv — no @PreAuthorize needed.
 *
 * Blueprint reference: implementation_plan.md Phase 5, Step 5.3
 */
@RestController
@RequestMapping("/v1/ai/routing")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "AI - Route Optimization", description = "Delivery route planning and optimization")
public class AiRoutingController {

    private final RouteSolver             routeSolver;
    private final DeliveryRouteRepository deliveryRouteRepository;
    private final SecurityUtils           securityUtils;

    // ── POST /optimize ────────────────────────────────────────────────────

    @PostMapping("/optimize")
    @Operation(
            summary = "Trigger route optimization for a distributor",
            description = "Builds optimized delivery routes for the given distributor and date. " +
                          "Uses Timefold VRP solver with Haversine distance estimation.")
    public ResponseEntity<ApiResponse<List<DeliveryRoute>>> optimize(
            @Parameter(required = true) @RequestParam UUID distributorId,
            @Parameter(description = "Target delivery date (ISO 8601). Defaults to tomorrow.")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate routeDate) {

        LocalDate targetDate = routeDate != null ? routeDate : LocalDate.now().plusDays(1);
        log.info("POST /v1/ai/routing/optimize distributor={} date={}", distributorId, targetDate);

        try {
            List<DeliveryRoute> routes = routeSolver.optimize(distributorId, targetDate);
            return ResponseEntity.ok(ApiResponse.success(routes));
        } catch (Exception e) {
            log.error("Route optimization failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Route optimization failed: " + e.getMessage()));
        }
    }

    // ── POST /reoptimize ──────────────────────────────────────────────────

    @PostMapping("/reoptimize")
    @Operation(
            summary = "Re-optimize an existing route (intraday)",
            description = "Adjusts an in-progress route after disruptions. " +
                          "Only PENDING stops are re-sequenced.")
    public ResponseEntity<ApiResponse<DeliveryRoute>> reoptimize(
            @Parameter(required = true) @RequestParam UUID routeId) {

        log.info("POST /v1/ai/routing/reoptimize routeId={}", routeId);

        try {
            DeliveryRoute route = routeSolver.reoptimize(routeId);
            return ResponseEntity.ok(ApiResponse.success(route));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Re-optimization failed for route={}: {}", routeId, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Re-optimization failed: " + e.getMessage()));
        }
    }

    // ── GET /routes/{date} ────────────────────────────────────────────────

    @GetMapping("/routes/{date}")
    @Operation(summary = "Get all routes for a distributor on a given date")
    public ResponseEntity<ApiResponse<List<DeliveryRoute>>> getRoutesByDate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Parameter(required = true) @RequestParam UUID distributorId) {

        log.info("GET /v1/ai/routing/routes/{} distributor={}", date, distributorId);

        List<DeliveryRoute> routes = deliveryRouteRepository
                .findActiveRoutesForDate(distributorId, date);

        return ResponseEntity.ok(ApiResponse.success(routes));
    }

    // ── GET /routes/{id} ─────────────────────────────────────────────────

    @GetMapping("/routes/{id}")
    @Operation(summary = "Get a single route by ID")
    public ResponseEntity<ApiResponse<DeliveryRoute>> getRouteById(@PathVariable UUID id) {

        log.info("GET /v1/ai/routing/routes/{}", id);

        return deliveryRouteRepository.findById(id)
                .map(r -> ResponseEntity.ok(ApiResponse.success(r)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // ── GET /my-route ─────────────────────────────────────────────────────

    @GetMapping("/my-route")
    @Operation(
            summary = "Get today's route for the authenticated driver",
            description = "Returns the delivery route assigned to the currently logged-in driver for today.")
    public ResponseEntity<ApiResponse<DeliveryRoute>> getMyRoute() {

        UUID driverId = securityUtils.getCurrentUserId();
        log.info("GET /v1/ai/routing/my-route driver={}", driverId);

        if (driverId == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Not authenticated"));
        }

        List<DeliveryRoute> routes = deliveryRouteRepository
                .findByDriverIdAndRouteDate(driverId, LocalDate.now());

        DeliveryRoute route = routes.isEmpty() ? null : routes.get(0);
        return ResponseEntity.ok(ApiResponse.success(route));
    }

    // ── PUT /routes/{routeId}/stops/{stopSequence}/complete ───────────────

    @PutMapping("/routes/{routeId}/stops/{stopSequence}/complete")
    @Operation(
            summary = "Mark a delivery stop as completed",
            description = "Updates the stop status to COMPLETED and records the actual arrival time. " +
                          "Automatically transitions the route status to IN_PROGRESS or COMPLETED.")
    public ResponseEntity<ApiResponse<DeliveryRoute>> completeStop(
            @PathVariable UUID routeId,
            @PathVariable int stopSequence) {

        log.info("PUT /v1/ai/routing/routes/{}/stops/{}/complete", routeId, stopSequence);

        return deliveryRouteRepository.findById(routeId)
                .<ResponseEntity<ApiResponse<DeliveryRoute>>>map(route -> {
                    List<Map<String, Object>> stops = route.getStopSequence();
                    if (stops != null) {
                        for (Map<String, Object> stop : stops) {
                            Object seq = stop.get("sequence");
                            if (seq != null && ((Number) seq).intValue() == stopSequence) {
                                stop.put("status", "COMPLETED");
                                stop.put("actualArrival", LocalDateTime.now().toString());
                                break;
                            }
                        }
                        route.setStopSequence(stops);

                        boolean allDone = stops.stream().allMatch(s -> {
                            Object st = s.get("status");
                            return "COMPLETED".equals(st) || "SKIPPED".equals(st);
                        });
                        if (allDone) {
                            route.setStatus(RouteStatus.COMPLETED);
                        } else if (route.getStatus() == RouteStatus.PLANNED) {
                            route.setStatus(RouteStatus.IN_PROGRESS);
                        }
                    }
                    DeliveryRoute saved = deliveryRouteRepository.save(route);
                    return ResponseEntity.ok(ApiResponse.success(saved));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // ── GET /history ──────────────────────────────────────────────────────

    @GetMapping("/history")
    @Operation(
            summary = "Get paginated route history for the current user's distributor",
            description = "Returns a paginated list of past routes ordered by date descending. " +
                          "Optionally filter by a specific date.")
    public ResponseEntity<ApiResponse<Page<RouteHistoryEntry>>> getHistory(
            @Parameter(description = "Filter by specific date (ISO 8601)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        UUID distributorId = securityUtils.getCurrentUserDistributorId();
        log.info("GET /v1/ai/routing/history distributor={} date={}", distributorId, date);

        if (distributorId == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Not authenticated"));
        }

        PageRequest pageable = PageRequest.of(page, size, Sort.by("routeDate").descending());

        Page<DeliveryRoute> routesPage = (date != null)
                ? deliveryRouteRepository.findByDistributorIdAndRouteDate(distributorId, date, pageable)
                : deliveryRouteRepository.findByDistributorId(distributorId, pageable);

        Page<RouteHistoryEntry> result = routesPage.map(r -> {
            List<Map<String, Object>> stops = r.getStopSequence() != null
                    ? r.getStopSequence() : List.of();
            int totalStops = stops.size();
            int completedStops = (int) stops.stream()
                    .filter(s -> "COMPLETED".equals(s.get("status")))
                    .count();
            return new RouteHistoryEntry(
                    r.getId(),
                    r.getRouteDate(),
                    1,
                    totalStops,
                    completedStops,
                    r.getTotalDistanceKm() != null ? r.getTotalDistanceKm() : 0.0,
                    r.getTotalDurationMin() != null ? r.getTotalDurationMin() : 0.0,
                    r.getActualDurationMin()
            );
        });

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // ── Inner DTO ─────────────────────────────────────────────────────────

    public record RouteHistoryEntry(
            UUID      id,
            LocalDate date,
            int       vehicleCount,
            int       totalStops,
            int       completedStops,
            double    totalDistanceKm,
            double    plannedDurationMinutes,
            Double    actualDurationMinutes
    ) {}
}
