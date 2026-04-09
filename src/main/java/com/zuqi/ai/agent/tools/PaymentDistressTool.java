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
public class PaymentDistressTool {

    private final AnomalyAlertRepository anomalyAlertRepository;

    @Tool("Get open payment distress alerts for a distributor. Returns the total count and the 5 most " +
          "recent unresolved payment distress cases. Payment distress is detected by an XGBoost classifier " +
          "that identifies merchants showing early signs of financial difficulty based on payment patterns, " +
          "credit utilization, and order behaviour — allowing early intervention before defaults occur.")
    @Transactional(readOnly = true)
    public String getPaymentDistressAlerts(
            @P("The distributor UUID") String distributorId,
            @P("Number of days to look back (e.g. 7, 30, 90). Default is 30.") String periodDays) {

        log.info("[TOOL CALLED] getPaymentDistressAlerts distributorId={}", distributorId);
        try {
            UUID distId = UUID.fromString(distributorId.trim());

            List<AnomalyAlert> distressAlerts = anomalyAlertRepository
                    .findByDistributorIdAndStatus(distId, AlertStatus.OPEN)
                    .stream()
                    .filter(a -> AlertType.PAYMENT_DISTRESS == a.getAlertType())
                    .collect(Collectors.toList());

            long totalOpen = distressAlerts.size();

            List<AnomalyAlert> recent5 = distressAlerts.stream()
                    .filter(a -> a.getCreatedAt() != null)
                    .sorted(Comparator.comparing(AnomalyAlert::getCreatedAt).reversed())
                    .limit(5)
                    .collect(Collectors.toList());

            StringBuilder sb = new StringBuilder();
            sb.append("{ \"tool\": \"PaymentDistressAlerts\", \"distributorId\": \"").append(distId).append("\", ");
            sb.append("\"totalOpenDistressAlerts\": ").append(totalOpen).append(", ");
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
            log.error("PaymentDistressTool: invalid distributorId '{}'", distributorId, e);
            return "{ \"error\": \"Invalid distributorId format: " + distributorId + "\" }";
        } catch (Exception e) {
            log.error("PaymentDistressTool: unexpected error for distributorId '{}'", distributorId, e);
            return "{ \"error\": \"Failed to retrieve payment distress alerts: " + e.getMessage() + "\" }";
        }
    }
}
