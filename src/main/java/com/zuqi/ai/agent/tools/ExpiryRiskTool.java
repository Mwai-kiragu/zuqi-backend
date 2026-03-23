package com.zuqi.ai.agent.tools;

import com.zuqi.domain.ai.ExpiryRiskScore;
import com.zuqi.repository.ExpiryRiskScoreRepository;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Agent tool: expiry risk scores for perishable product batches.
 *
 * <p>Reads from ai_expiry_risk_scores (pre-computed by ExpiryRiskJob daily at 5:30 AM).
 * Phase 7 tool — does NOT call the ML model directly; reads pre-computed results.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExpiryRiskTool {

    private final ExpiryRiskScoreRepository expiryRiskScoreRepository;

    @Tool("Get expiry risk scores for perishable product batches in a distributor. " +
          "Returns batch details, risk score (0-1), risk tier (NORMAL/MODERATE/HIGH/CRITICAL), " +
          "recommended action (NORMAL/DISCOUNT/REDISTRIBUTE/QUARANTINE), " +
          "days to expiry, and suggested discount percentage. " +
          "Only returns batches with risk score >= 0.3 (at least MODERATE risk). " +
          "Parameter: distributorId (UUID string).")
    @Transactional(readOnly = true)
    public String getExpiryRisks(@P("The distributor UUID") String distributorId) {
        log.info("[TOOL CALLED] getExpiryRisks distributorId={}", distributorId);
        try {
            UUID distId = UUID.fromString(distributorId.trim());
            List<ExpiryRiskScore> risks =
                    expiryRiskScoreRepository.findHighRiskByDistributor(distId, 0.3);

            long critical = risks.stream().filter(r -> "CRITICAL".equals(r.getRiskTier())).count();
            long high     = risks.stream().filter(r -> "HIGH".equals(r.getRiskTier())).count();
            long moderate = risks.stream().filter(r -> "MODERATE".equals(r.getRiskTier())).count();

            StringBuilder sb = new StringBuilder();
            sb.append("{ \"tool\": \"ExpiryRisk\", \"distributorId\": \"").append(distId).append("\", ");
            sb.append("\"summary\": { \"total\": ").append(risks.size())
              .append(", \"critical\": ").append(critical)
              .append(", \"high\": ").append(high)
              .append(", \"moderate\": ").append(moderate).append(" }, ");
            sb.append("\"batches\": [");

            for (int i = 0; i < risks.size(); i++) {
                ExpiryRiskScore r = risks.get(i);
                String productName = r.getProduct() != null
                        ? r.getProduct().getName().replace("\"", "'") : "Unknown";
                String warehouseName = r.getWarehouse() != null
                        ? r.getWarehouse().getName().replace("\"", "'") : "Unknown";
                sb.append(String.format(
                        "{ \"product\": \"%s\", \"warehouse\": \"%s\", \"batch\": \"%s\", " +
                        "\"daysToExpiry\": %d, \"riskScore\": %.2f, \"riskTier\": \"%s\", " +
                        "\"action\": \"%s\", \"discountPct\": %.1f }",
                        productName, warehouseName,
                        r.getBatchNumber() != null ? r.getBatchNumber() : "N/A",
                        r.getDaysToExpiry() != null ? r.getDaysToExpiry() : 0,
                        r.getRiskScore() != null ? r.getRiskScore() : 0.0,
                        r.getRiskTier() != null ? r.getRiskTier() : "UNKNOWN",
                        r.getRecommendedAction() != null ? r.getRecommendedAction() : "NORMAL",
                        r.getDiscountSuggestionPct() != null ? r.getDiscountSuggestionPct() : 0.0));
                if (i < risks.size() - 1) sb.append(", ");
            }
            sb.append("] }");
            return sb.toString();

        } catch (IllegalArgumentException e) {
            return "{ \"error\": \"Invalid distributorId format: " + distributorId + "\" }";
        } catch (Exception e) {
            log.error("ExpiryRiskTool error for distributorId '{}'", distributorId, e);
            return "{ \"error\": \"Failed to retrieve expiry risks: " + e.getMessage() + "\" }";
        }
    }
}
