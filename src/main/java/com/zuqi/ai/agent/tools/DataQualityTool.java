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
public class DataQualityTool {

    private final AnomalyAlertRepository anomalyAlertRepository;

    @Tool("Get open data quality alerts for a distributor. Returns the total count and details of the " +
          "5 most recent open data quality issues. Data quality alerts are raised when ML detects " +
          "statistical anomalies in incoming data records (orders, payments, stock adjustments) that " +
          "suggest duplicate entries, impossible values, or missing required fields.")
    @Transactional(readOnly = true)
    public String getDataQualityAlerts(
            @P("The distributor UUID") String distributorId,
            @P("Number of days to look back (e.g. 7, 30). Default is 30.") String periodDays) {

        log.info("[TOOL CALLED] getDataQualityAlerts distributorId={}", distributorId);
        try {
            UUID distId = UUID.fromString(distributorId.trim());

            List<AnomalyAlert> qualityAlerts = anomalyAlertRepository
                    .findByDistributorIdAndStatus(distId, AlertStatus.OPEN)
                    .stream()
                    .filter(a -> AlertType.DATA_QUALITY == a.getAlertType())
                    .collect(Collectors.toList());

            long totalOpen = qualityAlerts.size();

            List<AnomalyAlert> recent5 = qualityAlerts.stream()
                    .filter(a -> a.getCreatedAt() != null)
                    .sorted(Comparator.comparing(AnomalyAlert::getCreatedAt).reversed())
                    .limit(5)
                    .collect(Collectors.toList());

            StringBuilder sb = new StringBuilder();
            sb.append("{ \"tool\": \"DataQualityAlerts\", \"distributorId\": \"").append(distId).append("\", ");
            sb.append("\"totalOpenDataQualityAlerts\": ").append(totalOpen).append(", ");
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
            log.error("DataQualityTool: invalid distributorId '{}'", distributorId, e);
            return "{ \"error\": \"Invalid distributorId format: " + distributorId + "\" }";
        } catch (Exception e) {
            log.error("DataQualityTool: unexpected error for distributorId '{}'", distributorId, e);
            return "{ \"error\": \"Failed to retrieve data quality alerts: " + e.getMessage() + "\" }";
        }
    }
}
