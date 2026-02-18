package com.zuqi.api.controller;

import com.zuqi.ai.routing.RouteSolver;
import com.zuqi.api.dto.ApiResponse;
import com.zuqi.domain.ai.DeliveryRoute;
import com.zuqi.repository.DeliveryRouteRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
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

    // ── POST /optimize ────────────────────────────────────────────────────

    @PostMapping("/optimize")
    @Operation(
            summary = "Trigger route optimization for a distributor",
            description = "Builds optimized delivery routes for the given distributor and date. " +
                          "Uses Timefold VRP solver with Haversine distance estimation.")
    public ResponseEntity<ApiResponse<OptimizeResponse>> optimize(
            @Parameter(required = true) @RequestParam UUID distributorId,
            @Parameter(description = "Target delivery date (ISO 8601). Defaults to tomorrow.")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate routeDate) {

        LocalDate targetDate = routeDate != null ? routeDate : LocalDate.now().plusDays(1);
        log.info("POST /v1/ai/routing/optimize distributor={} date={}", distributorId, targetDate);

        try {
            List<DeliveryRoute> routes = routeSolver.optimize(distributorId, targetDate);

            OptimizeResponse response = new OptimizeResponse(
                    distributorId, targetDate, routes.size(),
                    routes.stream().mapToInt(r -> r.getStopSequence() != null
                            ? r.getStopSequence().size() : 0).sum(),
                    routes.stream().mapToDouble(r -> r.getTotalDistanceKm() != null
                            ? r.getTotalDistanceKm() : 0.0).sum(),
                    routes.stream().map(DeliveryRoute::getId).toList()
            );

            return ResponseEntity.ok(ApiResponse.success(response));

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

        UUID routeId = java.util.Objects.requireNonNull(id);
        return deliveryRouteRepository.findById(routeId)
                .map(r -> ResponseEntity.ok(ApiResponse.success(r)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // ── Inner DTOs ────────────────────────────────────────────────────────

    public record OptimizeResponse(
            UUID        distributorId,
            LocalDate   routeDate,
            int         routesCreated,
            int         totalStops,
            double      totalDistanceKm,
            List<UUID>  routeIds
    ) {}
}
