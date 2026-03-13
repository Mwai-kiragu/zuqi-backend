package com.zuqi.ai.assistant;

import com.zuqi.ai.agent.tools.*;
import com.zuqi.ai.assistant.tools.CreditSummaryTool;
import com.zuqi.ai.assistant.tools.DemandForecastSummaryTool;
import com.zuqi.domain.ai.ReportType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Assembles data context for report generation by calling existing tools directly
 * (without going through the LLM tool-calling loop), then invokes AssistantReportAiService
 * to generate a professional markdown report.
 *
 * Each report type pulls a specific subset of tool data — not all tools for every report.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AssistantReportBuilder {

    private final AssistantReportAiService reportAiService;
    private final SalesTrendTool           salesTrendTool;
    private final InventoryHealthTool      inventoryHealthTool;
    private final PaymentPerformanceTool   paymentPerformanceTool;
    private final RepPerformanceTool       repPerformanceTool;
    private final MerchantMetricsTool      merchantMetricsTool;
    private final AnomalyAlertsTool        anomalyAlertsTool;
    private final DeliveryMetricsTool      deliveryMetricsTool;
    private final CreditSummaryTool        creditSummaryTool;
    private final DemandForecastSummaryTool demandForecastSummaryTool;

    /**
     * Gather data for the given reportType, build a structured context, call the LLM.
     *
     * @param distributorId the distributor to report on
     * @param reportType    the type of report to generate
     * @param params        optional parameters (e.g. {"periodDays": 30})
     * @return markdown-formatted report string
     */
    public String build(UUID distributorId, ReportType reportType, Map<String, Object> params) {
        String distIdStr   = distributorId.toString();
        String periodDays  = String.valueOf(params.getOrDefault("periodDays", "30"));

        log.info("Building {} report for distributor={} periodDays={}", reportType, distIdStr, periodDays);

        String dataBlock = switch (reportType) {
            case SALES            -> assembleSalesData(distIdStr, periodDays);
            case INVENTORY        -> assembleInventoryData(distIdStr);
            case PAYMENT          -> assemblePaymentData(distIdStr, periodDays);
            case CREDIT_RISK      -> assembleCreditData(distIdStr);
            case REP_PERFORMANCE  -> assembleRepData(distIdStr);
            case MERCHANT_SUMMARY -> assembleMerchantData(distIdStr, periodDays);
            case DEMAND_FORECAST  -> assembleDemandData(distIdStr);
            case ANOMALY_SUMMARY  -> assembleAnomalyData(distIdStr, periodDays);
        };

        String context = """
                REPORT GENERATION REQUEST
                =========================
                Report Type  : %s
                Distributor  : %s
                Period       : Last %s days
                Generated At : %s

                RAW DATA
                --------
                %s

                Generate a complete professional %s report using the data above.
                """.formatted(reportType, distributorId, periodDays,
                              LocalDate.now(), dataBlock, reportType);

        return reportAiService.generateReport(context);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Data assembly methods — call tools directly, combine results
    // ─────────────────────────────────────────────────────────────────────────

    private String assembleSalesData(String distId, String periodDays) {
        return "SALES TREND:\n" + salesTrendTool.getSalesTrend(distId, periodDays)
             + "\n\nMERCHANT METRICS:\n" + merchantMetricsTool.getMerchantMetrics(distId);
    }

    private String assembleInventoryData(String distId) {
        return "INVENTORY HEALTH:\n" + inventoryHealthTool.getInventoryHealth(distId)
             + "\n\nANOMALY ALERTS:\n" + anomalyAlertsTool.getAnomalyAlerts(distId, "30");
    }

    private String assemblePaymentData(String distId, String periodDays) {
        return "PAYMENT PERFORMANCE:\n" + paymentPerformanceTool.getPaymentPerformance(distId)
             + "\n\nSALES TREND:\n" + salesTrendTool.getSalesTrend(distId, periodDays);
    }

    private String assembleCreditData(String distId) {
        return "CREDIT SUMMARY:\n" + creditSummaryTool.getCreditSummary(distId)
             + "\n\nMERCHANT METRICS:\n" + merchantMetricsTool.getMerchantMetrics(distId)
             + "\n\nANOMALY ALERTS:\n" + anomalyAlertsTool.getAnomalyAlerts(distId, "30");
    }

    private String assembleRepData(String distId) {
        return "REP PERFORMANCE:\n" + repPerformanceTool.getRepPerformance(distId)
             + "\n\nSALES TREND:\n" + salesTrendTool.getSalesTrend(distId, "30")
             + "\n\nDELIVERY METRICS:\n" + deliveryMetricsTool.getDeliveryMetrics(distId);
    }

    private String assembleMerchantData(String distId, String periodDays) {
        return "MERCHANT METRICS:\n" + merchantMetricsTool.getMerchantMetrics(distId)
             + "\n\nSALES TREND:\n" + salesTrendTool.getSalesTrend(distId, periodDays)
             + "\n\nPAYMENT PERFORMANCE:\n" + paymentPerformanceTool.getPaymentPerformance(distId)
             + "\n\nCREDIT SUMMARY:\n" + creditSummaryTool.getCreditSummary(distId);
    }

    private String assembleDemandData(String distId) {
        return "DEMAND FORECAST SUMMARY:\n" + demandForecastSummaryTool.getDemandForecastSummary(distId)
             + "\n\nINVENTORY HEALTH:\n" + inventoryHealthTool.getInventoryHealth(distId)
             + "\n\nSALES TREND:\n" + salesTrendTool.getSalesTrend(distId, "30");
    }

    private String assembleAnomalyData(String distId, String periodDays) {
        return "ANOMALY ALERTS:\n" + anomalyAlertsTool.getAnomalyAlerts(distId, periodDays)
             + "\n\nINVENTORY HEALTH:\n" + inventoryHealthTool.getInventoryHealth(distId)
             + "\n\nPAYMENT PERFORMANCE:\n" + paymentPerformanceTool.getPaymentPerformance(distId);
    }
}
