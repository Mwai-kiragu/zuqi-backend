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
public class ShrinkageAlertTool {

    private final AnomalyAlertRepository anomalyAlertRepository;

    @Tool("Get open inventory shrinkage anomaly alerts for a distributor. Returns total open shrinkage " +
          "alerts and details of the 5 most recent ones, including warehouse, product, severity, and description. " +
          "Shrinkage alerts are raised when stock disappears faster than recorded sales can explain, " +
          "indicating possible theft, spoilage, or recording errors.")
    @Transactional(readOnly = true)
    public String getShrinkageAlerts(
            @P("The distributor UUID") String distributorId,
            @P("Number of days to look back (e.g. 7, 30). Default is 30.") String periodDays) {

        log.info("[TOOL CALLED] getShrinkageAlerts distributorId={}", distributorId);
        try {
            UUID distId = UUID.fromString(distributorId.trim());

            List<AnomalyAlert> shrinkageAlerts = anomalyAlertRepository
                    .findByDistributorIdAndStatus(distId, AlertStatus.OPEN)
                    .stream()
                    .filter(a -> AlertType.SHRINKAGE == a.getAlertType())
                    .collect(Collectors.toList());

            long totalOpen = shrinkageAlerts.size();

            List<AnomalyAlert> recent5 = shrinkageAlerts.stream()
                    .filter(a -> a.getCreatedAt() != null)
                    .sorted(Comparator.comparing(AnomalyAlert::getCreatedAt).reversed())
                    .limit(5)
                    .collect(Collectors.toList());

            StringBuilder sb = new StringBuilder();
            sb.append("{ \"tool\": \"ShrinkageAlerts\", \"distributorId\": \"").append(distId).append("\", ");
            sb.append("\"totalOpenShrinkageAlerts\": ").append(totalOpen).append(", ");
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
            log.error("ShrinkageAlertTool: invalid distributorId '{}'", distributorId, e);
            return "{ \"error\": \"Invalid distributorId format: " + distributorId + "\" }";
        } catch (Exception e) {
            log.error("ShrinkageAlertTool: unexpected error for distributorId '{}'", distributorId, e);
            return "{ \"error\": \"Failed to retrieve shrinkage alerts: " + e.getMessage() + "\" }";
        }
    }
}
