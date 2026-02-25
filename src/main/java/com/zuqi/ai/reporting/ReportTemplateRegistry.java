package com.zuqi.ai.reporting;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Registry of principal-specific compliance report templates.
 *
 * Each principal manufacturer (Unilever, P&G, EABL) requires a different
 * tone, section structure, and set of required metrics in their compliance reports.
 * A DEFAULT template is used as fallback for unlisted principals.
 *
 * Blueprint reference: implementation_plan.md Phase 6 Task 6.3
 */
@Service
@Slf4j
public class ReportTemplateRegistry {

    /**
     * Immutable report template definition for a principal manufacturer.
     *
     * @param principal       Company name (display label, e.g. "Unilever Kenya")
     * @param sections        Comma-separated ordered section headings
     * @param tone            Narrative tone guidance for the LLM
     * @param requiredMetrics Comma-separated metric names that must appear in the report
     */
    public record ReportTemplate(
            String principal,
            String sections,
            String tone,
            String requiredMetrics
    ) {}

    private final Map<String, ReportTemplate> templates;

    public ReportTemplateRegistry() {
        templates = Map.of(
                "UNILEVER", new ReportTemplate(
                        "Unilever Kenya",
                        "Executive Summary, Sales Performance, Inventory Health, Payment Collection, Delivery Efficiency, SKU Distribution Coverage, Recommendations",
                        "professional and data-driven, with emphasis on market coverage and SKU penetration metrics",
                        "total_orders, order_fulfillment_rate, on_time_delivery_rate, payment_collection_rate, sku_coverage_percent, active_merchants_count"
                ),
                "P_AND_G", new ReportTemplate(
                        "Procter & Gamble Kenya",
                        "Executive Summary, Compliance Status, Sales Performance, Route Efficiency, Inventory Availability, Payment Health, Corrective Actions",
                        "formal and compliance-focused, with clear corrective action items and measurable targets",
                        "compliance_score, total_revenue_ksh, stockout_incidents, average_route_completion_rate, payment_overdue_rate, credit_utilization_avg"
                ),
                "EABL", new ReportTemplate(
                        "East African Breweries Limited",
                        "Executive Summary, Volume Performance, Distribution Coverage, Cold Chain Compliance, Credit & Payment, Route Adherence, Forward Plan",
                        "results-oriented and concise, with volume-first language and clear period-over-period comparisons",
                        "volume_cases_sold, distribution_points_reached, cold_chain_compliance_rate, credit_days_outstanding, route_adherence_percent, forecast_accuracy"
                ),
                "DEFAULT", new ReportTemplate(
                        "Principal Manufacturer",
                        "Executive Summary, Sales Performance, Inventory Health, Payment Collection, Delivery Efficiency, Recommendations",
                        "professional and data-driven",
                        "total_orders, order_fulfillment_rate, on_time_delivery_rate, payment_collection_rate, active_merchants_count"
                )
        );

        log.info("ReportTemplateRegistry initialised with {} templates: {}",
                templates.size(), templates.keySet());
    }

    /**
     * Retrieve the template for the given principal key.
     * Lookup is case-insensitive. Falls back to DEFAULT if no match found.
     *
     * @param principal principal key (e.g. "UNILEVER", "unilever", "P_AND_G")
     * @return matching template, or DEFAULT template
     */
    public ReportTemplate getTemplate(String principal) {
        if (principal == null || principal.isBlank()) {
            log.debug("No principal provided — using DEFAULT template");
            return templates.get("DEFAULT");
        }

        String key = principal.trim().toUpperCase().replace("&", "_AND_").replace(" ", "_");
        ReportTemplate template = templates.get(key);

        if (template == null) {
            log.debug("No template found for principal='{}' (normalised key='{}') — using DEFAULT", principal, key);
            return templates.get("DEFAULT");
        }

        log.debug("Resolved template for principal='{}': {}", principal, template.principal());
        return template;
    }

    /**
     * Returns the list of all supported principal keys that have dedicated templates.
     * DEFAULT is excluded as it is a fallback, not a discrete principal.
     *
     * @return immutable list of supported principal keys
     */
    public List<String> getSupportedPrincipals() {
        return templates.keySet().stream()
                .filter(k -> !"DEFAULT".equals(k))
                .sorted()
                .toList();
    }
}
