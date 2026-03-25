package com.zuqi.ai.agent.tools;

import com.zuqi.domain.ai.CustomerHealthScore;
import com.zuqi.repository.CustomerHealthScoreRepository;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Agent tool: customer health score tier distribution and at-risk customer list.
 *
 * <p>Reads from ai_customer_health_scores (pre-computed by CustomerHealthScoreJob weekly).
 * Formula-based composite score — no AI confidence modifier needed.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CustomerHealthTool {

    private final CustomerHealthScoreRepository customerHealthScoreRepository;

    @Tool("Get customer health scores for a distributor. " +
          "Returns health tier distribution (THRIVING, HEALTHY, NEEDS_ATTENTION, AT_RISK, CRITICAL) " +
          "and the top 10 customers needing attention (AT_RISK and CRITICAL tiers). " +
          "Use this to prioritise sales rep outreach and retention efforts. " +
          "Parameter: distributorId (UUID string).")
    @Transactional(readOnly = true)
    public String getCustomerHealth(@P("The distributor UUID") String distributorId) {
        log.info("[TOOL CALLED] getCustomerHealth distributorId={}", distributorId);
        try {
            UUID distId = UUID.fromString(distributorId.trim());

            long thriving      = customerHealthScoreRepository.findByDistributorIdAndHealthTier(distId, "THRIVING").size();
            long healthy       = customerHealthScoreRepository.findByDistributorIdAndHealthTier(distId, "HEALTHY").size();
            long needsAttention = customerHealthScoreRepository.findByDistributorIdAndHealthTier(distId, "NEEDS_ATTENTION").size();
            List<CustomerHealthScore> atRisk   = customerHealthScoreRepository.findByDistributorIdAndHealthTier(distId, "AT_RISK");
            List<CustomerHealthScore> critical = customerHealthScoreRepository.findByDistributorIdAndHealthTier(distId, "CRITICAL");

            StringBuilder sb = new StringBuilder();
            sb.append("{ \"tool\": \"CustomerHealth\", \"distributorId\": \"").append(distId).append("\", ");
            sb.append("\"distribution\": {");
            sb.append("\"THRIVING\": ").append(thriving).append(", ");
            sb.append("\"HEALTHY\": ").append(healthy).append(", ");
            sb.append("\"NEEDS_ATTENTION\": ").append(needsAttention).append(", ");
            sb.append("\"AT_RISK\": ").append(atRisk.size()).append(", ");
            sb.append("\"CRITICAL\": ").append(critical.size());
            sb.append("}, \"priorityCustomers\": [");

            // Top 10 from CRITICAL first, then AT_RISK
            List<CustomerHealthScore> priority = new java.util.ArrayList<>(critical);
            priority.addAll(atRisk);
            int limit = Math.min(10, priority.size());
            for (int i = 0; i < limit; i++) {
                CustomerHealthScore h = priority.get(i);
                String name = h.getCustomer() != null
                        ? h.getCustomer().getBusinessName().replace("\"", "'") : "Unknown";
                sb.append(String.format(
                        "{ \"customer\": \"%s\", \"tier\": \"%s\", \"score\": %.1f }",
                        name, h.getHealthTier(), h.getHealthScore() != null ? h.getHealthScore() : 0.0));
                if (i < limit - 1) sb.append(", ");
            }
            sb.append("] }");
            return sb.toString();

        } catch (IllegalArgumentException e) {
            return "{ \"error\": \"Invalid distributorId format: " + distributorId + "\" }";
        } catch (Exception e) {
            log.error("CustomerHealthTool error for distributorId '{}'", distributorId, e);
            return "{ \"error\": \"Failed to retrieve customer health: " + e.getMessage() + "\" }";
        }
    }
}
