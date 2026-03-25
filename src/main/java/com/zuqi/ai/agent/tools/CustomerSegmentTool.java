package com.zuqi.ai.agent.tools;

import com.zuqi.repository.CustomerSegmentRepository;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Agent tool: customer segment distribution summary.
 *
 * <p>Reads from ai_customer_segments (pre-computed by CustomerSegmentationJob weekly).
 * No confidence modifier — K-Means cluster assignments are direct counts.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CustomerSegmentTool {

    private final CustomerSegmentRepository customerSegmentRepository;

    private static final String[] SEGMENT_LABELS = {
            "HIGH_VALUE_GROWING", "STABLE_MID_TIER", "AT_RISK_DECLINING",
            "NEW_LOW_ACTIVITY", "HIGH_VALUE_AT_RISK"
    };

    @Tool("Get customer segment distribution for a distributor. " +
          "Returns the count of customers in each segment: " +
          "HIGH_VALUE_GROWING, STABLE_MID_TIER, AT_RISK_DECLINING, NEW_LOW_ACTIVITY, HIGH_VALUE_AT_RISK. " +
          "Use this to understand the health and composition of the distributor's customer base. " +
          "Parameter: distributorId (UUID string).")
    @Transactional(readOnly = true)
    public String getCustomerSegments(@P("The distributor UUID") String distributorId) {
        log.info("[TOOL CALLED] getCustomerSegments distributorId={}", distributorId);
        try {
            UUID distId = UUID.fromString(distributorId.trim());

            StringBuilder sb = new StringBuilder();
            sb.append("{ \"tool\": \"CustomerSegments\", \"distributorId\": \"").append(distId).append("\", ");
            sb.append("\"segments\": {");

            long total = 0;
            for (int i = 0; i < SEGMENT_LABELS.length; i++) {
                long count = customerSegmentRepository
                        .countByDistributorIdAndSegmentLabel(distId, SEGMENT_LABELS[i]);
                total += count;
                sb.append("\"").append(SEGMENT_LABELS[i]).append("\": ").append(count);
                if (i < SEGMENT_LABELS.length - 1) sb.append(", ");
            }
            sb.append("}, \"totalSegmented\": ").append(total).append(" }");
            return sb.toString();

        } catch (IllegalArgumentException e) {
            return "{ \"error\": \"Invalid distributorId format: " + distributorId + "\" }";
        } catch (Exception e) {
            log.error("CustomerSegmentTool error for distributorId '{}'", distributorId, e);
            return "{ \"error\": \"Failed to retrieve customer segments: " + e.getMessage() + "\" }";
        }
    }
}
