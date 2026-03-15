package com.zuqi.ai.agent.tools;

import com.zuqi.domain.ai.DeliveryRoute;
import com.zuqi.domain.ai.RouteStatus;
import com.zuqi.repository.DeliveryRouteRepository;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.OptionalDouble;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class DeliveryMetricsTool {

    private final DeliveryRouteRepository deliveryRouteRepository;

    @Tool("Get delivery route metrics for a distributor. Returns totalRoutes (all routes ever created), " +
          "plannedRoutes, inProgressRoutes, completedRoutes, cancelledRoutes, " +
          "avgDistanceKm (average planned distance in km across COMPLETED routes), " +
          "avgLoadUtilizationPct (average load utilisation percentage across COMPLETED routes), " +
          "and avgDurationMin (average planned duration in minutes across COMPLETED routes). " +
          "Parameter: distributorId (UUID string).")
    @Transactional(readOnly = true)
    public String getDeliveryMetrics(@P("The distributor UUID") String distributorId) {
        try {
            UUID distId = UUID.fromString(distributorId.trim());

            // Fetch routes by each status to avoid loading all routes in one call
            List<DeliveryRoute> plannedRoutes    = deliveryRouteRepository
                    .findByDistributorIdAndStatus(distId, RouteStatus.PLANNED);
            List<DeliveryRoute> inProgressRoutes = deliveryRouteRepository
                    .findByDistributorIdAndStatus(distId, RouteStatus.IN_PROGRESS);
            List<DeliveryRoute> completedRoutes  = deliveryRouteRepository
                    .findByDistributorIdAndStatus(distId, RouteStatus.COMPLETED);
            List<DeliveryRoute> cancelledRoutes  = deliveryRouteRepository
                    .findByDistributorIdAndStatus(distId, RouteStatus.CANCELLED);

            int plannedCount    = plannedRoutes.size();
            int inProgressCount = inProgressRoutes.size();
            int completedCount  = completedRoutes.size();
            int cancelledCount  = cancelledRoutes.size();
            int totalRoutes     = plannedCount + inProgressCount + completedCount + cancelledCount;

            // Averages computed over COMPLETED routes only
            OptionalDouble avgDistanceKm = completedRoutes.stream()
                    .filter(r -> r.getTotalDistanceKm() != null)
                    .mapToDouble(DeliveryRoute::getTotalDistanceKm)
                    .average();

            OptionalDouble avgLoadUtilizationPct = completedRoutes.stream()
                    .filter(r -> r.getLoadUtilizationPct() != null)
                    .mapToDouble(DeliveryRoute::getLoadUtilizationPct)
                    .average();

            OptionalDouble avgDurationMin = completedRoutes.stream()
                    .filter(r -> r.getTotalDurationMin() != null)
                    .mapToDouble(DeliveryRoute::getTotalDurationMin)
                    .average();

            String avgDistStr  = avgDistanceKm.isPresent()
                    ? String.format("%.2f", avgDistanceKm.getAsDouble())    : "N/A";
            String avgLoadStr  = avgLoadUtilizationPct.isPresent()
                    ? String.format("%.1f", avgLoadUtilizationPct.getAsDouble()) : "N/A";
            String avgDurStr   = avgDurationMin.isPresent()
                    ? String.format("%.1f", avgDurationMin.getAsDouble())   : "N/A";

            return String.format(
                    "{ \"tool\": \"DeliveryMetrics\", \"distributorId\": \"%s\", " +
                    "\"totalRoutes\": %d, \"planned\": %d, \"inProgress\": %d, " +
                    "\"completed\": %d, \"cancelled\": %d, " +
                    "\"avgDistanceKm\": \"%s\", \"avgLoadUtilizationPct\": \"%s\", " +
                    "\"avgDurationMin\": \"%s\" }",
                    distId,
                    totalRoutes, plannedCount, inProgressCount,
                    completedCount, cancelledCount,
                    avgDistStr, avgLoadStr, avgDurStr
            );

        } catch (IllegalArgumentException e) {
            log.error("DeliveryMetricsTool: invalid distributorId '{}'", distributorId, e);
            return "{ \"error\": \"Invalid distributorId format: " + distributorId + "\" }";
        } catch (Exception e) {
            log.error("DeliveryMetricsTool: unexpected error for distributorId '{}'", distributorId, e);
            return "{ \"error\": \"Failed to retrieve delivery metrics: " + e.getMessage() + "\" }";
        }
    }
}
