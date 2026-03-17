package com.zuqi.ai.assistant;

import com.zuqi.ai.agent.tools.*;
import com.zuqi.ai.assistant.tools.CreditSummaryTool;
import com.zuqi.ai.assistant.tools.DemandForecastSummaryTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

/**
 * Fetches business data directly in Java (no LLM tool calling) and returns
 * a role-scoped context string injected into the LLM prompt.
 *
 * Role data access:
 *  DRIVER            → deliveries only
 *  SALES_REP         → sales, customers, deliveries, demand forecasts
 *  WAREHOUSE_MANAGER → inventory, deliveries, anomaly alerts
 *  FINANCE           → payments, credit, sales
 *  MERCHANT_ADMIN    → (own merchant data only — limited context)
 *  DISTRIBUTOR_ADMIN / SUPER_ADMIN → all data
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AssistantContextFetcher {

    private final SalesTrendTool            salesTrendTool;
    private final InventoryHealthTool       inventoryHealthTool;
    private final PaymentPerformanceTool    paymentPerformanceTool;
    private final RepPerformanceTool        repPerformanceTool;
    private final MerchantMetricsTool       merchantMetricsTool;
    private final AnomalyAlertsTool         anomalyAlertsTool;
    private final DeliveryMetricsTool       deliveryMetricsTool;
    private final CreditSummaryTool         creditSummaryTool;
    private final DemandForecastSummaryTool demandForecastSummaryTool;

    /**
     * Fetch role-scoped business context for a distributor.
     * @param distributorId  The distributor to fetch data for
     * @param userRole       The primary role of the requesting user (e.g. "DRIVER", "SALES_REP")
     */
    public String fetchContext(UUID distributorId, String userRole) {
        String id = distributorId.toString();
        log.debug("AssistantContextFetcher: fetching context for distributor={} role={}", id, userRole);

        Set<String> allowedSections = allowedSections(userRole);

        StringBuilder ctx = new StringBuilder();
        ctx.append("=== LIVE BUSINESS DATA (role-scoped for ").append(userRole).append(") ===\n\n");

        if (allowedSections.contains("SALES")) {
            ctx.append("SALES (last 30 days):\n").append(safe(() -> salesTrendTool.getSalesTrend(id, "30"))).append("\n\n");
        }
        if (allowedSections.contains("INVENTORY")) {
            ctx.append("INVENTORY:\n").append(safe(() -> inventoryHealthTool.getInventoryHealth(id))).append("\n\n");
        }
        if (allowedSections.contains("PAYMENTS")) {
            ctx.append("PAYMENTS:\n").append(safe(() -> paymentPerformanceTool.getPaymentPerformance(id))).append("\n\n");
        }
        if (allowedSections.contains("REPS")) {
            ctx.append("SALES REPS:\n").append(safe(() -> repPerformanceTool.getRepPerformance(id))).append("\n\n");
        }
        if (allowedSections.contains("CUSTOMERS")) {
            ctx.append("CUSTOMERS:\n").append(safe(() -> merchantMetricsTool.getMerchantMetrics(id))).append("\n\n");
        }
        if (allowedSections.contains("ANOMALY")) {
            ctx.append("ANOMALY ALERTS:\n").append(safe(() -> anomalyAlertsTool.getAnomalyAlerts(id, "30"))).append("\n\n");
        }
        if (allowedSections.contains("DELIVERIES")) {
            ctx.append("DELIVERIES:\n").append(safe(() -> deliveryMetricsTool.getDeliveryMetrics(id))).append("\n\n");
        }
        if (allowedSections.contains("CREDIT")) {
            ctx.append("CREDIT:\n").append(safe(() -> creditSummaryTool.getCreditSummary(id))).append("\n\n");
        }
        if (allowedSections.contains("DEMAND")) {
            ctx.append("DEMAND FORECASTS:\n").append(safe(() -> demandForecastSummaryTool.getDemandForecastSummary(id))).append("\n\n");
        }

        ctx.append("=== END OF BUSINESS DATA ===");
        return ctx.toString();
    }

    /** Backward-compatible overload — defaults to full access (SUPER_ADMIN behaviour). */
    public String fetchContext(UUID distributorId) {
        return fetchContext(distributorId, "SUPER_ADMIN");
    }

    @CacheEvict(value = "assistant-context", key = "#distributorId")
    public void evictContext(UUID distributorId) {
        log.debug("AssistantContextFetcher: evicted context cache for distributor={}", distributorId);
    }

    /**
     * Returns the set of data sections a given role may see.
     */
    private static Set<String> allowedSections(String role) {
        if (role == null) return Set.of();
        return switch (role.toUpperCase()) {
            case "DRIVER"            -> Set.of("DELIVERIES");
            case "SALES_REP"         -> Set.of("SALES", "CUSTOMERS", "DELIVERIES", "DEMAND");
            case "WAREHOUSE_MANAGER" -> Set.of("INVENTORY", "DELIVERIES", "ANOMALY");
            case "FINANCE"           -> Set.of("PAYMENTS", "CREDIT", "SALES");
            case "MERCHANT_ADMIN"    -> Set.of("SALES", "INVENTORY", "PAYMENTS", "ANOMALY", "DEMAND");
            case "CUSTOMER",
                 "MERCHANT"          -> Set.of("SALES", "PAYMENTS");
            // DISTRIBUTOR_ADMIN, SUPER_ADMIN, or anything unknown → full access
            default                  -> Set.of("SALES", "INVENTORY", "PAYMENTS", "REPS",
                                                "CUSTOMERS", "ANOMALY", "DELIVERIES",
                                                "CREDIT", "DEMAND");
        };
    }

    private String safe(java.util.function.Supplier<String> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            log.warn("AssistantContextFetcher: tool call failed — {}", e.getMessage());
            return "{\"error\": \"data unavailable\"}";
        }
    }
}
