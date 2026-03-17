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
          "avgDistanceKm, avgLoadUtilizationPct, avgDurationMin (all over COMPLETED routes), " +
          "and the top 5 in-progress routes with route date, driver name, stop count, and distance. " +
          "Parameter: distributorId (UUID string).")
    @Transactional(readOnly = true)
    public String getDeliveryMetrics(@P("The distributor UUID") String distributorId) {
        log.info("[TOOL CALLED] getDeliveryMetrics distributorId={}", distributorId);
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

            // Top 5 in-progress routes with details
            List<DeliveryRoute> top5InProgress = inProgressRoutes.stream().limit(5).collect(java.util.stream.Collectors.toList());

            StringBuilder sb = new StringBuilder();
            sb.append(String.format(
                    "{ \"tool\": \"DeliveryMetrics\", \"distributorId\": \"%s\", " +
                    "\"totalRoutes\": %d, \"planned\": %d, \"inProgress\": %d, " +
                    "\"completed\": %d, \"cancelled\": %d, " +
                    "\"avgDistanceKm\": \"%s\", \"avgLoadUtilizationPct\": \"%s\", " +
                    "\"avgDurationMin\": \"%s\", ",
                    distId,
                    totalRoutes, plannedCount, inProgressCount,
                    completedCount, cancelledCount,
                    avgDistStr, avgLoadStr, avgDurStr));

            sb.append("\"inProgressRoutes\": [");
            for (int i = 0; i < top5InProgress.size(); i++) {
                DeliveryRoute r = top5InProgress.get(i);
                String routeDate = r.getRouteDate() != null ? r.getRouteDate().toString() : "unknown";
                String driver = "Unassigned";
                if (r.getDriver() != null) {
                    String first = r.getDriver().getFirstName() != null ? r.getDriver().getFirstName() : "";
                    String last  = r.getDriver().getLastName()  != null ? r.getDriver().getLastName()  : "";
                    driver = (first + " " + last).trim().replace("\"", "'");
                }
                int stopCount   = r.getStopSequence() != null ? r.getStopSequence().size() : 0;
                String distKm   = r.getTotalDistanceKm() != null ? String.format("%.2f", r.getTotalDistanceKm()) : "N/A";
                sb.append(String.format(
                        "{ \"routeDate\": \"%s\", \"driver\": \"%s\", \"stops\": %d, \"distanceKm\": \"%s\" }",
                        routeDate, driver, stopCount, distKm));
                if (i < top5InProgress.size() - 1) sb.append(", ");
            }
            sb.append("] }");

            return sb.toString();

        } catch (IllegalArgumentException e) {
            log.error("DeliveryMetricsTool: invalid distributorId '{}'", distributorId, e);
            return "{ \"error\": \"Invalid distributorId format: " + distributorId + "\" }";
        } catch (Exception e) {
            log.error("DeliveryMetricsTool: unexpected error for distributorId '{}'", distributorId, e);
            return "{ \"error\": \"Failed to retrieve delivery metrics: " + e.getMessage() + "\" }";
        }
    }
}
