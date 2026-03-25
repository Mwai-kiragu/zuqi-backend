package com.zuqi.ai.agent.tools;

import com.zuqi.domain.ai.ChurnPrediction;
import com.zuqi.repository.ChurnPredictionRepository;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Agent tool: churn risk predictions for customers.
 *
 * <p>Reads from ai_churn_predictions (pre-computed by ChurnPredictionJob weekly).
 * Returns HIGH and CRITICAL risk customers with recommended retention actions.
 * AI output — confidence modifier already applied during prediction.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChurnRiskTool {

    private final ChurnPredictionRepository churnPredictionRepository;

    @Tool("Get churn risk predictions for a distributor. " +
          "Returns customers at high risk of churning (churn probability >= 0.6), " +
          "their risk tier (HIGH or CRITICAL), churn probability, days since last order, " +
          "top churn factor, and recommended retention action. " +
          "Use this to identify which customers need urgent outreach to prevent churn. " +
          "Parameter: distributorId (UUID string).")
    @Transactional(readOnly = true)
    public String getChurnRisk(@P("The distributor UUID") String distributorId) {
        log.info("[TOOL CALLED] getChurnRisk distributorId={}", distributorId);
        try {
            UUID distId = UUID.fromString(distributorId.trim());
            List<ChurnPrediction> atRisk =
                    churnPredictionRepository.findAtRiskCustomers(distId, 0.6);

            long critical = atRisk.stream().filter(c -> "CRITICAL".equals(c.getRiskTier())).count();
            long high     = atRisk.stream().filter(c -> "HIGH".equals(c.getRiskTier())).count();

            StringBuilder sb = new StringBuilder();
            sb.append("{ \"tool\": \"ChurnRisk\", \"distributorId\": \"").append(distId).append("\", ");
            sb.append("\"summary\": { \"atRiskCount\": ").append(atRisk.size())
              .append(", \"critical\": ").append(critical)
              .append(", \"high\": ").append(high).append(" }, ");
            sb.append("\"customers\": [");

            int limit = Math.min(20, atRisk.size());
            for (int i = 0; i < limit; i++) {
                ChurnPrediction c = atRisk.get(i);
                String name = c.getCustomer() != null
                        ? c.getCustomer().getBusinessName().replace("\"", "'") : "Unknown";
                sb.append(String.format(
                        "{ \"customer\": \"%s\", \"tier\": \"%s\", " +
                        "\"churnProbability\": %.2f, \"daysSinceLastOrder\": %s, " +
                        "\"topFactor\": \"%s\", \"action\": \"%s\" }",
                        name,
                        c.getRiskTier() != null ? c.getRiskTier() : "UNKNOWN",
                        c.getChurnProbability() != null ? c.getChurnProbability() : 0.0,
                        c.getDaysSinceLastOrder() != null ? c.getDaysSinceLastOrder().toString() : "null",
                        c.getTopChurnFactor() != null ? c.getTopChurnFactor().replace("\"", "'") : "N/A",
                        c.getRecommendedAction() != null ? c.getRecommendedAction() : "FOLLOW_UP"));
                if (i < limit - 1) sb.append(", ");
            }
            sb.append("] }");
            return sb.toString();

        } catch (IllegalArgumentException e) {
            return "{ \"error\": \"Invalid distributorId format: " + distributorId + "\" }";
        } catch (Exception e) {
            log.error("ChurnRiskTool error for distributorId '{}'", distributorId, e);
            return "{ \"error\": \"Failed to retrieve churn risk: " + e.getMessage() + "\" }";
        }
    }
}
