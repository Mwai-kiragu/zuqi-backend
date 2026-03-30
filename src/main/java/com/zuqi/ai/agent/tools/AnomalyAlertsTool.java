package com.zuqi.ai.agent.tools;

import com.zuqi.domain.ai.AlertSeverity;
import com.zuqi.domain.ai.AlertStatus;
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
public class AnomalyAlertsTool {
    private final AnomalyAlertRepository anomalyAlertRepository;
    @Tool("Get open anomaly alerts for a distributor. Returns the total number of open alerts " +
          "broken down by severity (CRITICAL, HIGH, MEDIUM, LOW), and the descriptions of the " +
          "5 most recent open alerts sorted by creation date descending. " +
          "Parameters: distributorId (UUID string), periodDays (informational only — all OPEN alerts " +
          "are returned regardless, as they represent unresolved issues).")
    @Transactional(readOnly = true)
    public String getAnomalyAlerts(
            @P("The distributor UUID") String distributorId,
            @P("Number of days to look back (e.g. 7, 30, 90). Default is 30.") String periodDays) {
        log.info("[TOOL CALLED] getAnomalyAlerts distributorId={}", distributorId);
        try {
            UUID distId = UUID.fromString(distributorId.trim());

            List<AnomalyAlert> openAlerts = anomalyAlertRepository.findByDistributorIdAndStatus(
                    distId, AlertStatus.OPEN);

            long totalOpen    = openAlerts.size();
            long criticalCount = openAlerts.stream()
                    .filter(a -> AlertSeverity.CRITICAL == a.getSeverity()).count();
            long highCount     = openAlerts.stream()
                    .filter(a -> AlertSeverity.HIGH     == a.getSeverity()).count();
            long mediumCount   = openAlerts.stream()
                    .filter(a -> AlertSeverity.MEDIUM   == a.getSeverity()).count();
            long lowCount      = openAlerts.stream()
                    .filter(a -> AlertSeverity.LOW      == a.getSeverity()).count();

            List<AnomalyAlert> acknowledgedAlerts = anomalyAlertRepository.findByDistributorIdAndStatus(
                    distId, AlertStatus.ACKNOWLEDGED);
            long totalAcknowledged = acknowledgedAlerts.size();

            List<AnomalyAlert> recent5 = openAlerts.stream()
                    .filter(a -> a.getCreatedAt() != null)
                    .sorted(Comparator.comparing(AnomalyAlert::getCreatedAt).reversed())
                    .limit(5)
                    .collect(Collectors.toList());

            StringBuilder sb = new StringBuilder();
            sb.append("{ \"tool\": \"AnomalyAlerts\", \"distributorId\": \"").append(distId).append("\", ");
            sb.append("\"totalOpen\": ").append(totalOpen).append(", ");
            sb.append("\"totalAcknowledged\": ").append(totalAcknowledged).append(", ");
            sb.append("\"bySeverity\": { ");
            sb.append("\"critical\": ").append(criticalCount).append(", ");
            sb.append("\"high\": ").append(highCount).append(", ");
            sb.append("\"medium\": ").append(mediumCount).append(", ");
            sb.append("\"low\": ").append(lowCount).append(" }, ");
            sb.append("\"recentAlerts\": [");

            for (int i = 0; i < recent5.size(); i++) {
                AnomalyAlert alert = recent5.get(i);
                String createdAt = alert.getCreatedAt() != null
                        ? alert.getCreatedAt().toLocalDate().toString()
                        : "unknown";
                // Escape quotes in description to avoid breaking JSON structure
                String desc = alert.getDescription() != null
                        ? alert.getDescription().replace("\"", "'")
                        : "";
                sb.append(String.format(
                        "{ \"alertType\": \"%s\", \"severity\": \"%s\", \"createdAt\": \"%s\", " +
                        "\"description\": \"%s\" }",
                        alert.getAlertType() != null ? alert.getAlertType().name() : "UNKNOWN",
                        alert.getSeverity()  != null ? alert.getSeverity().name()  : "UNKNOWN",
                        createdAt,
                        desc
                ));
                if (i < recent5.size() - 1) sb.append(", ");
            }

            sb.append("] }");
            return sb.toString();

        } catch (IllegalArgumentException e) {
            log.error("AnomalyAlertsTool: invalid distributorId '{}'", distributorId, e);
            return "{ \"error\": \"Invalid distributorId format: " + distributorId + "\" }";
        } catch (Exception e) {
            log.error("AnomalyAlertsTool: unexpected error for distributorId '{}'", distributorId, e);
            return "{ \"error\": \"Failed to retrieve anomaly alerts: " + e.getMessage() + "\" }";
        }
    }
}
