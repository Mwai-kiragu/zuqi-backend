package com.zuqi.ai.agent.tools;

import com.zuqi.domain.ai.AlertStatus;
import com.zuqi.domain.ai.AlertType;
import com.zuqi.domain.ai.AnomalyAlert;
import com.zuqi.repository.AnomalyAlertRepository;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class StockoutAlertTool {

    private final AnomalyAlertRepository anomalyAlertRepository;

    @Tool("Get open stockout risk alerts for a distributor. Returns the total number of products " +
          "currently flagged as high stockout risk and details of the 5 most urgent ones. " +
          "Stockout alerts are raised by the ML classifier when predicted days-until-stockout " +
          "falls below the reorder threshold, allowing proactive replenishment.")
    @Transactional(readOnly = true)
    public String getStockoutAlerts(
            @P("The distributor UUID") String distributorId,
            @P("Number of days to look back (e.g. 7, 30). Default is 30.") String periodDays) {

        log.info("[TOOL CALLED] getStockoutAlerts distributorId={}", distributorId);
        try {
            UUID distId = UUID.fromString(distributorId.trim());

            List<AnomalyAlert> stockoutAlerts = anomalyAlertRepository
                    .findByDistributorIdAndStatus(distId, AlertStatus.OPEN)
                    .stream()
                    .filter(a -> AlertType.STOCKOUT_RISK == a.getAlertType())
                    .collect(Collectors.toList());

            long totalOpen = stockoutAlerts.size();

            List<AnomalyAlert> recent5 = stockoutAlerts.stream()
                    .filter(a -> a.getCreatedAt() != null)
                    .sorted(Comparator.comparing(AnomalyAlert::getCreatedAt).reversed())
                    .limit(5)
                    .collect(Collectors.toList());

            StringBuilder sb = new StringBuilder();
            sb.append("{ \"tool\": \"StockoutAlerts\", \"distributorId\": \"").append(distId).append("\", ");
            sb.append("\"totalOpenStockoutAlerts\": ").append(totalOpen).append(", ");
            sb.append("\"recentAlerts\": [");

            for (int i = 0; i < recent5.size(); i++) {
                AnomalyAlert alert = recent5.get(i);
                String desc = alert.getDescription() != null
                        ? alert.getDescription().replace("\"", "'") : "";
                String createdAt = alert.getCreatedAt() != null
                        ? alert.getCreatedAt().toLocalDate().toString() : "unknown";
                sb.append(String.format(
                        "{ \"severity\": \"%s\", \"createdAt\": \"%s\", \"description\": \"%s\" }",
                        alert.getSeverity() != null ? alert.getSeverity().name() : "UNKNOWN",
                        createdAt, desc));
                if (i < recent5.size() - 1) sb.append(", ");
            }

            sb.append("] }");
            return sb.toString();

        } catch (IllegalArgumentException e) {
            log.error("StockoutAlertTool: invalid distributorId '{}'", distributorId, e);
            return "{ \"error\": \"Invalid distributorId format: " + distributorId + "\" }";
        } catch (Exception e) {
            log.error("StockoutAlertTool: unexpected error for distributorId '{}'", distributorId, e);
            return "{ \"error\": \"Failed to retrieve stockout alerts: " + e.getMessage() + "\" }";
        }
    }
}
