package com.zuqi.ai.agent.tools;

import com.zuqi.domain.ai.ReorderSuggestion;
import com.zuqi.repository.ReorderSuggestionRepository;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Agent tool: AI-generated reorder suggestions (EOQ-based formula).
 *
 * <p>Reads from ai_reorder_suggestions (pre-computed by ReorderOptimizationJob daily at 5 AM).
 * Formula-based — no AI confidence modifier needed.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReorderSuggestionTool {

    private final ReorderSuggestionRepository reorderSuggestionRepository;

    @Tool("Get AI-generated reorder suggestions for a distributor. " +
          "Returns products that need restocking with suggested quantity (EOQ), " +
          "reorder point, safety stock, days of supply remaining, and lead time. " +
          "Only returns PENDING suggestions (not yet converted to purchase requisitions). " +
          "Parameter: distributorId (UUID string).")
    @Transactional(readOnly = true)
    public String getReorderSuggestions(@P("The distributor UUID") String distributorId) {
        log.info("[TOOL CALLED] getReorderSuggestions distributorId={}", distributorId);
        try {
            UUID distId = UUID.fromString(distributorId.trim());
            List<ReorderSuggestion> suggestions =
                    reorderSuggestionRepository.findByDistributorIdAndStatus(distId, "PENDING");

            StringBuilder sb = new StringBuilder();
            sb.append("{ \"tool\": \"ReorderSuggestions\", \"distributorId\": \"").append(distId).append("\", ");
            sb.append("\"pendingCount\": ").append(suggestions.size()).append(", ");
            sb.append("\"suggestions\": [");

            for (int i = 0; i < suggestions.size(); i++) {
                ReorderSuggestion s = suggestions.get(i);
                String productName = s.getProduct() != null
                        ? s.getProduct().getName().replace("\"", "'") : "Unknown";
                String warehouseName = s.getWarehouse() != null
                        ? s.getWarehouse().getName().replace("\"", "'") : "Unknown";
                sb.append(String.format(
                        "{ \"product\": \"%s\", \"warehouse\": \"%s\", " +
                        "\"suggestedQty\": %.0f, \"reorderPoint\": %.0f, " +
                        "\"safetyStock\": %.0f, \"eoq\": %.0f, " +
                        "\"daysOfSupplyRemaining\": %.1f, \"leadTimeDays\": %s, " +
                        "\"confidence\": %.2f }",
                        productName, warehouseName,
                        s.getSuggestedQty() != null ? s.getSuggestedQty() : 0.0,
                        s.getReorderPoint() != null ? s.getReorderPoint() : 0.0,
                        s.getSafetyStock() != null ? s.getSafetyStock() : 0.0,
                        s.getEconomicOrderQty() != null ? s.getEconomicOrderQty() : 0.0,
                        s.getDaysOfSupplyRemaining() != null ? s.getDaysOfSupplyRemaining() : 0.0,
                        s.getLeadTimeDays() != null ? s.getLeadTimeDays().toString() : "null",
                        s.getConfidenceScore() != null ? s.getConfidenceScore() : 0.0));
                if (i < suggestions.size() - 1) sb.append(", ");
            }
            sb.append("] }");
            return sb.toString();

        } catch (IllegalArgumentException e) {
            return "{ \"error\": \"Invalid distributorId format: " + distributorId + "\" }";
        } catch (Exception e) {
            log.error("ReorderSuggestionTool error for distributorId '{}'", distributorId, e);
            return "{ \"error\": \"Failed to retrieve reorder suggestions: " + e.getMessage() + "\" }";
        }
    }
}
