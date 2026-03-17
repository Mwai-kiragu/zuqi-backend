package com.zuqi.ai.agent.tools;

import com.zuqi.domain.user.User;
import com.zuqi.repository.OrderRepository;
import com.zuqi.repository.UserRepository;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class RepPerformanceTool {

    private final UserRepository  userRepository;
    private final OrderRepository orderRepository;

    @Tool("Get sales representative performance for a distributor. Returns the total number of active " +
          "sales reps, their individual order counts for the current dataset, and identifies the " +
          "top 3 and bottom 3 performers by order volume. " +
          "Parameter: distributorId (UUID string).")
    @Transactional(readOnly = true)
    public String getRepPerformance(@P("The distributor UUID") String distributorId) {
        log.info("[TOOL CALLED] getRepPerformance distributorId={}", distributorId);
        try {
            UUID distId = UUID.fromString(distributorId.trim());

            // Fetch all active users for this distributor
            List<User> activeUsers = userRepository.findByDistributorIdAndActiveTrue(distId);

            // Filter to SALES_REP role only
            List<User> salesReps = activeUsers.stream()
                    .filter(u -> u.getRoles() != null &&
                                 u.getRoles().stream()
                                             .anyMatch(r -> "SALES_REP".equals(r.getName())))
                    .collect(Collectors.toList());

            int totalReps = salesReps.size();

            if (totalReps == 0) {
                return String.format(
                        "{ \"tool\": \"RepPerformance\", \"distributorId\": \"%s\", " +
                        "\"totalSalesReps\": 0, \"topPerformers\": [], \"bottomPerformers\": [], " +
                        "\"message\": \"No active sales reps found for this distributor\" }",
                        distId);
            }

            // Build rep summary with order counts; use Page with size=1 to get totalElements
            record RepSummary(String repId, String name, long orderCount) {}

            List<RepSummary> repSummaries = salesReps.stream()
                    .map(rep -> {
                        long orderCount = orderRepository.findBySalesRepId(
                                rep.getId(), PageRequest.of(0, 1)).getTotalElements();
                        return new RepSummary(
                                rep.getId().toString(),
                                rep.getFirstName() + " " + rep.getLastName(),
                                orderCount
                        );
                    })
                    .sorted(Comparator.comparingLong(RepSummary::orderCount).reversed())
                    .collect(Collectors.toList());

            // Top 3 performers
            List<RepSummary> top3 = repSummaries.stream().limit(3).collect(Collectors.toList());

            // Bottom 3 performers (reversed order, taking last 3)
            List<RepSummary> bottom3 = repSummaries.stream()
                    .sorted(Comparator.comparingLong(RepSummary::orderCount))
                    .limit(3)
                    .collect(Collectors.toList());

            StringBuilder sb = new StringBuilder();
            sb.append("{ \"tool\": \"RepPerformance\", \"distributorId\": \"").append(distId).append("\", ");
            sb.append("\"totalSalesReps\": ").append(totalReps).append(", ");

            sb.append("\"topPerformers\": [");
            for (int i = 0; i < top3.size(); i++) {
                RepSummary r = top3.get(i);
                sb.append(String.format("{ \"repId\": \"%s\", \"name\": \"%s\", \"orderCount\": %d }",
                        r.repId(), r.name(), r.orderCount()));
                if (i < top3.size() - 1) sb.append(", ");
            }
            sb.append("], ");

            sb.append("\"bottomPerformers\": [");
            for (int i = 0; i < bottom3.size(); i++) {
                RepSummary r = bottom3.get(i);
                sb.append(String.format("{ \"repId\": \"%s\", \"name\": \"%s\", \"orderCount\": %d }",
                        r.repId(), r.name(), r.orderCount()));
                if (i < bottom3.size() - 1) sb.append(", ");
            }
            sb.append("] }");

            return sb.toString();

        } catch (IllegalArgumentException e) {
            log.error("RepPerformanceTool: invalid distributorId '{}'", distributorId, e);
            return "{ \"error\": \"Invalid distributorId format: " + distributorId + "\" }";
        } catch (Exception e) {
            log.error("RepPerformanceTool: unexpected error for distributorId '{}'", distributorId, e);
            return "{ \"error\": \"Failed to retrieve rep performance: " + e.getMessage() + "\" }";
        }
    }
}
