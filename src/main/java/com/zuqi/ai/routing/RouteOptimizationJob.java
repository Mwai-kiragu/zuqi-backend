package com.zuqi.ai.routing;

import com.zuqi.domain.ai.DeliveryRoute;
import com.zuqi.repository.DistributorRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * Nightly batch job that pre-computes optimized delivery routes
 * for the following day across all active distributors.
 *
 * Runs at 6 PM (configurable via {@code zuqi.ai.routing.optimization-cron}).
 * Disabled by default — enable with {@code zuqi.ai.routing.enabled=true}.
 *
 * Blueprint reference: implementation_plan.md Phase 5, Step 5.3
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "zuqi.ai.routing", name = "enabled", havingValue = "true", matchIfMissing = false)
public class RouteOptimizationJob {

    private final RouteSolver          routeSolver;
    private final DistributorRepository distributorRepository;
    private final MeterRegistry         meterRegistry;

    @Scheduled(cron = "${zuqi.ai.routing.optimization-cron:0 0 18 * * *}")
    public void runNightlyOptimization() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        log.info("{}", "=".repeat(80));
        log.info("NIGHTLY ROUTE OPTIMIZATION - date={}", tomorrow);
        log.info("{}", "=".repeat(80));

        List<com.zuqi.domain.distributor.Distributor> distributors =
                distributorRepository.findAll();

        int totalRoutes = 0;
        int totalStops  = 0;
        int failures    = 0;

        for (com.zuqi.domain.distributor.Distributor distributor : distributors) {
            try {
                List<DeliveryRoute> routes = routeSolver.optimize(distributor.getId(), tomorrow);
                totalRoutes += routes.size();
                totalStops  += routes.stream()
                        .mapToInt(r -> r.getStopSequence() != null ? r.getStopSequence().size() : 0)
                        .sum();

                log.info("Distributor={} routes={}", distributor.getId(), routes.size());

            } catch (Exception e) {
                failures++;
                log.error("Route optimization failed for distributor={}: {}",
                        distributor.getId(), e.getMessage(), e);
            }
        }

        log.info("Nightly optimization complete: distributors={} routes={} stops={} failures={}",
                distributors.size(), totalRoutes, totalStops, failures);

        recordMetrics(totalRoutes, totalStops, failures);
    }

    private void recordMetrics(int routes, int stops, int failures) {
        Counter.builder("zuqi_ai_route_optimization_runs")
                .description("Nightly route optimization job runs")
                .register(meterRegistry).increment();

        meterRegistry.gauge("zuqi_ai_route_optimization_routes_last", routes);
        meterRegistry.gauge("zuqi_ai_route_optimization_stops_last",  stops);

        if (failures > 0) {
            Counter.builder("zuqi_ai_route_optimization_failures")
                    .description("Route optimization distributor failures")
                    .register(meterRegistry).increment(failures);
        }
    }
}
