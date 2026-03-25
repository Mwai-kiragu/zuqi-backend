package com.zuqi.ai.agent.tools;

import com.zuqi.domain.ai.SupplierRiskScore;
import com.zuqi.repository.SupplierRiskScoreRepository;
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
 * Agent tool: supplier risk scores for procurement decisions.
 *
 * <p>Reads from ai_supplier_risk_scores (pre-computed by SupplierRiskJob monthly).
 * Formula-based composite score — no AI confidence modifier needed.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SupplierRiskTool {

    private final SupplierRiskScoreRepository supplierRiskScoreRepository;

    @Tool("Get supplier risk scores for a distributor. " +
          "Returns suppliers ranked by risk (lowest score = highest risk), " +
          "their risk tier (PREFERRED/RELIABLE/ACCEPTABLE/AT_RISK/CRITICAL), " +
          "and sub-scores for delivery reliability, quality, price consistency, and responsiveness. " +
          "Use this to identify risky suppliers before placing large orders. " +
          "Parameter: distributorId (UUID string).")
    @Transactional(readOnly = true)
    public String getSupplierRisk(@P("The distributor UUID") String distributorId) {
        log.info("[TOOL CALLED] getSupplierRisk distributorId={}", distributorId);
        try {
            UUID distId = UUID.fromString(distributorId.trim());
            List<SupplierRiskScore> scores = supplierRiskScoreRepository
                    .findByDistributorId(distId, PageRequest.of(0, 50))
                    .getContent();

            long atRisk   = scores.stream().filter(s -> "AT_RISK".equals(s.getRiskTier())).count();
            long critical  = scores.stream().filter(s -> "CRITICAL".equals(s.getRiskTier())).count();
            long preferred = scores.stream().filter(s -> "PREFERRED".equals(s.getRiskTier())).count();

            StringBuilder sb = new StringBuilder();
            sb.append("{ \"tool\": \"SupplierRisk\", \"distributorId\": \"").append(distId).append("\", ");
            sb.append("\"summary\": { \"total\": ").append(scores.size())
              .append(", \"preferred\": ").append(preferred)
              .append(", \"atRisk\": ").append(atRisk)
              .append(", \"critical\": ").append(critical).append(" }, ");
            sb.append("\"suppliers\": [");

            for (int i = 0; i < scores.size(); i++) {
                SupplierRiskScore s = scores.get(i);
                String supplierName = s.getSupplier() != null
                        ? s.getSupplier().getName().replace("\"", "'") : "Unknown";
                sb.append(String.format(
                        "{ \"supplier\": \"%s\", \"tier\": \"%s\", \"score\": %.1f, " +
                        "\"delivery\": %.1f, \"quality\": %.1f, " +
                        "\"priceConsistency\": %.1f, \"responsiveness\": %.1f }",
                        supplierName,
                        s.getRiskTier() != null ? s.getRiskTier() : "UNKNOWN",
                        s.getRiskScore() != null ? s.getRiskScore() : 0.0,
                        s.getDeliveryReliabilityScore() != null ? s.getDeliveryReliabilityScore() : 0.0,
                        s.getQualityScore() != null ? s.getQualityScore() : 0.0,
                        s.getPriceConsistencyScore() != null ? s.getPriceConsistencyScore() : 0.0,
                        s.getResponsivenessScore() != null ? s.getResponsivenessScore() : 0.0));
                if (i < scores.size() - 1) sb.append(", ");
            }
            sb.append("] }");
            return sb.toString();

        } catch (IllegalArgumentException e) {
            return "{ \"error\": \"Invalid distributorId format: " + distributorId + "\" }";
        } catch (Exception e) {
            log.error("SupplierRiskTool error for distributorId '{}'", distributorId, e);
            return "{ \"error\": \"Failed to retrieve supplier risk: " + e.getMessage() + "\" }";
        }
    }
}
