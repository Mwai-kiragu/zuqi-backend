package com.zuqi.ai.assistant;

import com.zuqi.ai.agent.tools.*;
import com.zuqi.ai.assistant.tools.CreditSummaryTool;
import com.zuqi.ai.assistant.tools.DemandForecastSummaryTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Fetches all 9 business data points directly in Java (no LLM tool calling).
 *
 * This is the primary data-fetching strategy for chat Q&A.
 * Each tool is called directly — same DB queries, same results — but without
 * requiring the LLM to support function/tool calling.
 *
 * The returned context string is injected into the LLM prompt so gpt-oss
 * (or any model) can answer questions from real data without needing to
 * call tools itself.
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
     * Fetch all business context for a distributor and return as a formatted string.
     * Cached in Redis for 5 minutes per distributorId — avoids 9 DB queries on every message.
     * Each tool call is individually guarded — a single failure won't abort the whole fetch.
     */
    @Cacheable(value = "assistant-context", key = "#distributorId")
    public String fetchContext(UUID distributorId) {
        String id = distributorId.toString();
        log.debug("AssistantContextFetcher: fetching context for distributor={}", id);

        StringBuilder ctx = new StringBuilder();
        ctx.append("=== LIVE BUSINESS DATA (fetched from database) ===\n\n");

        ctx.append("SALES (last 30 days):\n").append(safe(() -> salesTrendTool.getSalesTrend(id, "30"))).append("\n\n");
        ctx.append("INVENTORY:\n").append(safe(() -> inventoryHealthTool.getInventoryHealth(id))).append("\n\n");
        ctx.append("PAYMENTS:\n").append(safe(() -> paymentPerformanceTool.getPaymentPerformance(id))).append("\n\n");
        ctx.append("SALES REPS:\n").append(safe(() -> repPerformanceTool.getRepPerformance(id))).append("\n\n");
        ctx.append("CUSTOMERS:\n").append(safe(() -> merchantMetricsTool.getMerchantMetrics(id))).append("\n\n");
        ctx.append("ANOMALY ALERTS:\n").append(safe(() -> anomalyAlertsTool.getAnomalyAlerts(id, "30"))).append("\n\n");
        ctx.append("DELIVERIES:\n").append(safe(() -> deliveryMetricsTool.getDeliveryMetrics(id))).append("\n\n");
        ctx.append("CREDIT:\n").append(safe(() -> creditSummaryTool.getCreditSummary(id))).append("\n\n");
        ctx.append("DEMAND FORECASTS:\n").append(safe(() -> demandForecastSummaryTool.getDemandForecastSummary(id))).append("\n\n");
        ctx.append("=== END OF BUSINESS DATA ===");

        return ctx.toString();
    }

    @CacheEvict(value = "assistant-context", key = "#distributorId")
    public void evictContext(UUID distributorId) {
        log.debug("AssistantContextFetcher: evicted context cache for distributor={}", distributorId);
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
