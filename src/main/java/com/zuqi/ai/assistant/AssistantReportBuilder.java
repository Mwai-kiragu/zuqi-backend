package com.zuqi.ai.assistant;

import com.zuqi.ai.agent.tools.*;
import com.zuqi.ai.assistant.tools.CreditSummaryTool;
import com.zuqi.ai.assistant.tools.DemandForecastSummaryTool;
import com.zuqi.ai.agent.tools.BalanceSheetTool;
import com.zuqi.ai.agent.tools.ProfitLossTool;
import com.zuqi.ai.agent.tools.TrialBalanceTool;
import com.zuqi.ai.agent.tools.CashFlowTool;
import com.zuqi.ai.agent.tools.ArAgingTool;
import com.zuqi.ai.agent.tools.ApAgingTool;
import com.zuqi.domain.ai.ReportType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Assembles data context for report generation by calling existing tools directly,
 * then generates each report section as a separate LLM call.
 *
 * Section-by-section generation keeps each prompt well within the 4096-token context
 * window. Prompts are tailored per report type for rich, targeted output.
 * The four sections are concatenated into one markdown document.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AssistantReportBuilder {

    private final AssistantReportAiService  reportAiService;
    private final SalesTrendTool            salesTrendTool;
    private final InventoryHealthTool       inventoryHealthTool;
    private final PaymentPerformanceTool    paymentPerformanceTool;
    private final RepPerformanceTool        repPerformanceTool;
    private final MerchantMetricsTool       merchantMetricsTool;
    private final AnomalyAlertsTool         anomalyAlertsTool;
    private final DeliveryMetricsTool       deliveryMetricsTool;
    private final CreditSummaryTool         creditSummaryTool;
    private final DemandForecastSummaryTool demandForecastSummaryTool;
    private final BalanceSheetTool          balanceSheetTool;
    private final ProfitLossTool            profitLossTool;
    private final TrialBalanceTool          trialBalanceTool;
    private final CashFlowTool              cashFlowTool;
    private final ArAgingTool               arAgingTool;
    private final ApAgingTool               apAgingTool;

    /**
     * Gather data for the given reportType, then generate the report section by section.
     * Each of the 4 sections is a focused LLM call with prompts specific to the report type.
     */
    public String build(UUID distributorId, ReportType reportType, Map<String, Object> params) {
        String distIdStr  = distributorId.toString();
        String periodDays = String.valueOf(params.getOrDefault("periodDays", "30"));
        String today      = LocalDate.now().toString();

        log.info("Building {} report section-by-section for distributor={} periodDays={}",
                reportType, distIdStr, periodDays);

        String data = switch (reportType) {
            case SALES            -> assembleSalesData(distIdStr, periodDays);
            case INVENTORY        -> assembleInventoryData(distIdStr);
            case PAYMENT          -> assemblePaymentData(distIdStr, periodDays);
            case CREDIT_RISK      -> assembleCreditData(distIdStr);
            case REP_PERFORMANCE  -> assembleRepData(distIdStr);
            case MERCHANT_SUMMARY -> assembleMerchantData(distIdStr, periodDays);
            case DEMAND_FORECAST  -> assembleDemandData(distIdStr);
            case ANOMALY_SUMMARY  -> assembleAnomalyData(distIdStr, periodDays);
            case BALANCE_SHEET    -> balanceSheetTool.getBalanceSheet(distIdStr);
            case PROFIT_LOSS      -> profitLossTool.getProfitLoss(distIdStr, periodDays);
            case TRIAL_BALANCE    -> trialBalanceTool.getTrialBalance(distIdStr);
            case CASH_FLOW        -> cashFlowTool.getCashFlow(distIdStr, periodDays);
            case AR_AGING         -> arAgingTool.getArAging(distIdStr);
            case AP_AGING         -> apAgingTool.getApAging(distIdStr);
        };

        String header = buildHeader(reportType, distributorId, periodDays, today);

        String[] sections = switch (reportType) {
            case SALES            -> buildSalesSections(data, periodDays);
            case INVENTORY        -> buildInventorySections(data);
            case PAYMENT          -> buildPaymentSections(data, periodDays);
            case CREDIT_RISK      -> buildCreditRiskSections(data);
            case REP_PERFORMANCE  -> buildRepPerformanceSections(data, periodDays);
            case MERCHANT_SUMMARY -> buildMerchantSummarySections(data, periodDays);
            case DEMAND_FORECAST  -> buildDemandForecastSections(data);
            case ANOMALY_SUMMARY  -> buildAnomalySummarySections(data, periodDays);
            case BALANCE_SHEET    -> buildBalanceSheetSections(data);
            case PROFIT_LOSS      -> buildProfitLossSections(data, periodDays);
            case TRIAL_BALANCE    -> buildTrialBalanceSections(data);
            case CASH_FLOW        -> buildCashFlowSections(data, periodDays);
            case AR_AGING         -> buildArAgingSections(data);
            case AP_AGING         -> buildApAgingSections(data);
        };

        log.info("Completed {} report (4 sections) for distributor={}", reportType, distIdStr);
        return header + "\n\n" + String.join("\n\n", sections);
    }

    // ── Header (no LLM — static) ──────────────────────────────────────────────

    private String buildHeader(ReportType type, UUID distributorId, String periodDays, String today) {
        String title = type.name().replace("_", " ");
        return "# %s Report\n**Generated:** %s | **Period:** Last %s days | **Distributor:** %s"
                .formatted(title, today, periodDays, distributorId);
    }

    // ── SALES ─────────────────────────────────────────────────────────────────

    private String[] buildSalesSections(String data, String periodDays) {
        log.debug("[SALES] Generating section 1/4: Executive Summary");
        String s1 = reportAiService.generateSection("""
                You are writing the Executive Summary for a Sales Performance Report for a Kenyan FMCG distributor.

                DATA:
                %s

                Write ONLY the ## Executive Summary section (2-3 sentences).
                Focus on: total revenue generated, total orders placed, order fulfilment rate \
                (delivered vs total), and whether sales performance is trending positively or needs attention.
                Quantify with KES and order counts from the data. Be direct and executive-ready.
                """.formatted(data));

        log.debug("[SALES] Generating section 2/4: Key Metrics");
        String s2 = reportAiService.generateSection("""
                You are writing the Key Metrics table for a Sales Performance Report.

                DATA:
                %s

                Write ONLY the ## Key Metrics section.
                Produce a markdown table with these columns: Metric | Value | Status
                Include ALL of the following rows (use data above, mark N/A if missing):
                - Total Revenue (KES)
                - Total Orders
                - Delivered Orders
                - Pending Orders
                - Cancelled Orders
                - Order Fulfilment Rate (%%)
                - Total Active Customers
                - New Customers (Last 30 days)
                - Customers with Recent Orders
                Format monetary values as KES X,XXX,XXX. Status = ✅ Good / ⚠ Watch / 🔴 Critical.
                """.formatted(data));

        log.debug("[SALES] Generating section 3/4: Analysis");
        String s3 = reportAiService.generateSection("""
                You are writing the Analysis section for a Sales Performance Report covering the last %s days.

                DATA:
                %s

                Write ONLY the ## Analysis section with these sub-sections:
                ### Revenue & Volume Trends
                Interpret total revenue and order volume. Is performance strong, average, or below par?

                ### Order Fulfilment
                Analyse the ratio of delivered vs pending vs cancelled orders. \
                Identify fulfilment bottlenecks or risks.

                ### Customer Engagement
                Comment on active customer count, new customer acquisition, and \
                whether the merchant base is growing or stagnating.

                Be data-driven. Reference specific numbers from the data. Avoid vague statements.
                """.formatted(data, data));

        log.debug("[SALES] Generating section 4/4: Recommendations");
        String s4 = reportAiService.generateSection("""
                You are writing the Recommendations section for a Sales Performance Report.

                DATA:
                %s

                Write ONLY the ## Recommendations section.
                Provide exactly 5 numbered recommendations. Each must:
                - Start with a **bold action verb phrase** (e.g. **Accelerate order processing for pending orders**)
                - Follow with 1-2 sentences explaining why and how, referencing the data
                Focus areas: revenue growth, reducing cancellations, improving fulfilment speed, \
                growing the active customer base, and converting inactive customers.
                """.formatted(data));

        return new String[]{s1, s2, s3, s4};
    }

    // ── INVENTORY ─────────────────────────────────────────────────────────────

    private String[] buildInventorySections(String data) {
        log.debug("[INVENTORY] Generating section 1/4: Executive Summary");
        String s1 = reportAiService.generateSection("""
                You are writing the Executive Summary for an Inventory Health Report for a Kenyan FMCG distributor.

                DATA:
                %s

                Write ONLY the ## Executive Summary section (2-3 sentences).
                Focus on: overall inventory health status (HEALTHY/WARNING/CRITICAL), \
                number of out-of-stock SKUs, number of SKUs below reorder level, \
                and the most critical anomaly alerts open. Be direct and flag urgency if warranted.
                """.formatted(data));

        log.debug("[INVENTORY] Generating section 2/4: Key Metrics");
        String s2 = reportAiService.generateSection("""
                You are writing the Key Metrics table for an Inventory Health Report.

                DATA:
                %s

                Write ONLY the ## Key Metrics section.
                Produce a markdown table: Metric | Value | Status
                Include:
                - Overall Health Status
                - Out-of-Stock SKUs
                - SKUs Below Reorder Level
                - Total Problematic SKUs
                - Open Anomaly Alerts (Critical)
                - Open Anomaly Alerts (High)
                - Open Anomaly Alerts (Medium / Low)
                Status = ✅ Good / ⚠ Watch / 🔴 Critical based on severity.
                """.formatted(data));

        log.debug("[INVENTORY] Generating section 3/4: Analysis");
        String s3 = reportAiService.generateSection("""
                You are writing the Analysis section for an Inventory Health Report.

                DATA:
                %s

                Write ONLY the ## Analysis section with these sub-sections:
                ### Stock Availability Risk
                Analyse out-of-stock and below-reorder-level SKUs. What is the risk to order fulfilment?

                ### Anomaly & Shrinkage Alerts
                Interpret the open anomaly alerts by severity. Identify the most urgent issues \
                and any patterns in alert types (e.g. shrinkage, data quality, payment anomalies).

                ### Inventory Health Trend
                Is the overall health status acceptable? What is driving the current status?

                Reference specific numbers. Be analytical, not descriptive.
                """.formatted(data));

        log.debug("[INVENTORY] Generating section 4/4: Recommendations");
        String s4 = reportAiService.generateSection("""
                You are writing the Recommendations section for an Inventory Health Report.

                DATA:
                %s

                Write ONLY the ## Recommendations section.
                Provide exactly 5 numbered recommendations. Each must:
                - Start with a **bold action phrase**
                - Explain the specific action and expected outcome in 1-2 sentences
                Focus on: emergency restocking of out-of-stock items, reorder triggering for \
                low-stock SKUs, investigating and resolving critical anomaly alerts, \
                improving stock data quality, and preventing future stockouts.
                """.formatted(data));

        return new String[]{s1, s2, s3, s4};
    }

    // ── PAYMENT ───────────────────────────────────────────────────────────────

    private String[] buildPaymentSections(String data, String periodDays) {
        log.debug("[PAYMENT] Generating section 1/4: Executive Summary");
        String s1 = reportAiService.generateSection("""
                You are writing the Executive Summary for a Payment Performance Report for a Kenyan FMCG distributor.

                DATA:
                %s

                Write ONLY the ## Executive Summary section (2-3 sentences).
                Focus on: total outstanding amount (KES), number of overdue orders, \
                payment completion rate, and the most critical payment risk. \
                Highlight if outstanding debt is at a concerning level. Be direct and concise.
                """.formatted(data));

        log.debug("[PAYMENT] Generating section 2/4: Key Metrics");
        String s2 = reportAiService.generateSection("""
                You are writing the Key Metrics table for a Payment Performance Report.

                DATA:
                %s

                Write ONLY the ## Key Metrics section.
                Produce a markdown table: Metric | Value | Status
                Include:
                - Total Outstanding Amount (KES)
                - Overdue Orders
                - Completed Payments
                - Pending Payments
                - Failed Payments
                - Unreconciled Payments
                - Total Revenue (period)
                - Total Orders (period)
                Status = ✅ Good / ⚠ Watch / 🔴 Critical.
                """.formatted(data));

        log.debug("[PAYMENT] Generating section 3/4: Analysis");
        String s3 = reportAiService.generateSection("""
                You are writing the Analysis section for a Payment Performance Report covering the last %s days.

                DATA:
                %s

                Write ONLY the ## Analysis section with these sub-sections:
                ### Outstanding Debt & Overdue Risk
                Analyse the total outstanding amount and overdue orders. \
                What is the cash flow risk? Is the overdue level manageable?

                ### Payment Completion & Failures
                Interpret completed vs pending vs failed payments. \
                Are there reconciliation gaps that need urgent attention?

                ### Revenue vs Collections
                Compare period revenue against outstanding amounts. \
                Is the distributor collecting efficiently relative to sales?

                Be specific — reference KES amounts and counts from the data.
                """.formatted(periodDays, data));

        log.debug("[PAYMENT] Generating section 4/4: Recommendations");
        String s4 = reportAiService.generateSection("""
                You are writing the Recommendations section for a Payment Performance Report.

                DATA:
                %s

                Write ONLY the ## Recommendations section.
                Provide exactly 5 numbered recommendations. Each must:
                - Start with a **bold action phrase**
                - Be specific to payment collection, reconciliation, or credit risk
                Focus on: chasing overdue collections, reconciling unreconciled payments, \
                reducing payment failures, improving credit terms for high-risk merchants, \
                and accelerating the payment cycle.
                """.formatted(data));

        return new String[]{s1, s2, s3, s4};
    }

    // ── CREDIT RISK ───────────────────────────────────────────────────────────

    private String[] buildCreditRiskSections(String data) {
        log.debug("[CREDIT_RISK] Generating section 1/4: Executive Summary");
        String s1 = reportAiService.generateSection("""
                You are writing the Executive Summary for a Credit Risk Report for a Kenyan FMCG distributor.

                DATA:
                %s

                Write ONLY the ## Executive Summary section (2-3 sentences).
                Focus on: total credit exposure (KES), overall utilisation rate (%%), \
                number of merchants at risk (>80%% utilisation), suspended limits, \
                and the level of credit risk the distributor currently carries. \
                Flag if the portfolio is at elevated risk. Be direct and executive-ready.
                """.formatted(data));

        log.debug("[CREDIT_RISK] Generating section 2/4: Key Metrics");
        String s2 = reportAiService.generateSection("""
                You are writing the Key Metrics table for a Credit Risk Report.

                DATA:
                %s

                Write ONLY the ## Key Metrics section.
                Produce a markdown table: Metric | Value | Status
                Include:
                - Total Credit Exposure (KES)
                - Total Utilized (KES)
                - Portfolio Utilisation Rate (%%)
                - Active Credit Limits (count)
                - Merchants at Risk (>80%% utilised)
                - Suspended Credit Limits
                - Total Customers
                - Open Anomaly Alerts (Critical + High)
                Status = ✅ Good / ⚠ Watch / 🔴 Critical. \
                Calculate utilisation rate as (utilized / exposure × 100) if not in data.
                """.formatted(data));

        log.debug("[CREDIT_RISK] Generating section 3/4: Analysis");
        String s3 = reportAiService.generateSection("""
                You are writing the Analysis section for a Credit Risk Report.

                DATA:
                %s

                Write ONLY the ## Analysis section with these sub-sections:
                ### Portfolio Exposure & Concentration
                How large is the total credit book? Is exposure concentrated in few merchants \
                or spread across many? What does the utilisation rate indicate?

                ### High-Risk Merchants
                Analyse merchants at >80%% utilisation and suspended limits. \
                What is the default risk? Are anomaly alerts correlated with high utilisation?

                ### Credit vs Sales Health
                How does credit utilisation relate to sales performance and payment behaviour? \
                Are high-utilisation merchants generating matching revenue?

                Use specific KES figures and counts. Identify the most actionable risk items.
                """.formatted(data));

        log.debug("[CREDIT_RISK] Generating section 4/4: Recommendations");
        String s4 = reportAiService.generateSection("""
                You are writing the Recommendations section for a Credit Risk Report.

                DATA:
                %s

                Write ONLY the ## Recommendations section.
                Provide exactly 5 numbered recommendations. Each must:
                - Start with a **bold action phrase**
                - Reference the specific risk metric or merchant group it addresses
                Focus on: reducing exposure for high-utilisation merchants, reviewing suspended limits, \
                improving collections from at-risk accounts, tightening credit approval criteria, \
                and aligning credit limits with actual payment history.
                """.formatted(data));

        return new String[]{s1, s2, s3, s4};
    }

    // ── REP PERFORMANCE ───────────────────────────────────────────────────────

    private String[] buildRepPerformanceSections(String data, String periodDays) {
        log.debug("[REP_PERFORMANCE] Generating section 1/4: Executive Summary");
        String s1 = reportAiService.generateSection("""
                You are writing the Executive Summary for a Sales Rep Performance Report for a Kenyan FMCG distributor.

                DATA:
                %s

                Write ONLY the ## Executive Summary section (2-3 sentences).
                Focus on: total sales reps, overall revenue generated, delivery completion rate, \
                and the performance gap between top and bottom reps. \
                Flag if underperformance is widespread or isolated. Be direct.
                """.formatted(data));

        log.debug("[REP_PERFORMANCE] Generating section 2/4: Key Metrics");
        String s2 = reportAiService.generateSection("""
                You are writing the Key Metrics table for a Sales Rep Performance Report.

                DATA:
                %s

                Write ONLY the ## Key Metrics section.
                Produce TWO tables:

                Table 1 — Team Overview: Metric | Value
                - Total Sales Reps
                - Total Orders (period)
                - Total Revenue (KES)
                - Delivered Orders
                - Total Routes
                - Completed Routes
                - Avg Route Distance (km)
                - Avg Load Utilisation (%%)

                Table 2 — Performance Spread: Rep Name | Orders | Tier
                List all top performers (Tier: 🏆 Top) and bottom performers (Tier: ⚠ Needs Attention).
                """.formatted(data));

        log.debug("[REP_PERFORMANCE] Generating section 3/4: Analysis");
        String s3 = reportAiService.generateSection("""
                You are writing the Analysis section for a Sales Rep Performance Report covering the last %s days.

                DATA:
                %s

                Write ONLY the ## Analysis section with these sub-sections:
                ### Team Performance Overview
                How is the overall team performing against order volume and revenue? \
                Is the team hitting expectations?

                ### Performance Disparity
                Analyse the gap between top and bottom performers. \
                Is the gap small (healthy competition) or large (structural problem)? \
                What could explain underperformance?

                ### Route & Delivery Efficiency
                Interpret route completion rates, average distance, and load utilisation. \
                Are routes being used efficiently?

                Be specific — name reps from the data where possible.
                """.formatted(periodDays, data));

        log.debug("[REP_PERFORMANCE] Generating section 4/4: Recommendations");
        String s4 = reportAiService.generateSection("""
                You are writing the Recommendations section for a Sales Rep Performance Report.

                DATA:
                %s

                Write ONLY the ## Recommendations section.
                Provide exactly 5 numbered recommendations. Each must:
                - Start with a **bold action phrase**
                - Be specific to rep development, route optimisation, or performance management
                Focus on: coaching bottom performers, rewarding top performers, \
                redistributing routes to improve coverage, setting clear targets, \
                and improving route load utilisation.
                """.formatted(data));

        return new String[]{s1, s2, s3, s4};
    }

    // ── MERCHANT SUMMARY ──────────────────────────────────────────────────────

    private String[] buildMerchantSummarySections(String data, String periodDays) {
        log.debug("[MERCHANT_SUMMARY] Generating section 1/4: Executive Summary");
        String s1 = reportAiService.generateSection("""
                You are writing the Executive Summary for a Customer (Merchant) Summary Report for a Kenyan FMCG distributor.

                DATA:
                %s

                Write ONLY the ## Executive Summary section (2-3 sentences).
                Focus on: total customer count, active vs inactive ratio, new customer acquisition, \
                total revenue from the period, outstanding payments, and credit exposure. \
                Identify the most important customer health signal. Be direct and concise.
                """.formatted(data));

        log.debug("[MERCHANT_SUMMARY] Generating section 2/4: Key Metrics");
        String s2 = reportAiService.generateSection("""
                You are writing the Key Metrics table for a Customer Summary Report.

                DATA:
                %s

                Write ONLY the ## Key Metrics section.
                Produce a markdown table: Metric | Value | Status
                Include:
                - Total Customers
                - Active Customers (ordered last 30 days)
                - Inactive Customers
                - New Customers (last 30 days)
                - Total Revenue (KES, period)
                - Total Orders
                - Outstanding Payments (KES)
                - Overdue Orders
                - Total Credit Exposure (KES)
                - Merchants at Credit Risk
                Status = ✅ Good / ⚠ Watch / 🔴 Critical.
                """.formatted(data));

        log.debug("[MERCHANT_SUMMARY] Generating section 3/4: Analysis");
        String s3 = reportAiService.generateSection("""
                You are writing the Analysis section for a Customer Summary Report covering the last %s days.

                DATA:
                %s

                Write ONLY the ## Analysis section with these sub-sections:
                ### Customer Base Health
                Analyse the active vs inactive customer ratio. Is the base growing or contracting? \
                What does new customer acquisition look like?

                ### Revenue & Payment Behaviour
                How does revenue relate to outstanding payments? Are customers paying reliably \
                or is debt accumulating?

                ### Credit Risk Exposure
                How significant is the credit book relative to the customer base? \
                Are at-risk customers a small minority or a systemic issue?

                Use specific numbers. Identify the most urgent segment to address.
                """.formatted(periodDays, data));

        log.debug("[MERCHANT_SUMMARY] Generating section 4/4: Recommendations");
        String s4 = reportAiService.generateSection("""
                You are writing the Recommendations section for a Customer Summary Report.

                DATA:
                %s

                Write ONLY the ## Recommendations section.
                Provide exactly 5 numbered recommendations. Each must:
                - Start with a **bold action phrase**
                - Address a specific customer segment or behaviour pattern
                Focus on: re-engaging inactive customers, accelerating collections from overdue accounts, \
                growing new customer acquisition, managing credit risk for high-exposure merchants, \
                and improving overall customer retention.
                """.formatted(data));

        return new String[]{s1, s2, s3, s4};
    }

    // ── DEMAND FORECAST ───────────────────────────────────────────────────────

    private String[] buildDemandForecastSections(String data) {
        log.debug("[DEMAND_FORECAST] Generating section 1/4: Executive Summary");
        String s1 = reportAiService.generateSection("""
                You are writing the Executive Summary for a Demand Forecast Report for a Kenyan FMCG distributor.

                DATA:
                %s

                Write ONLY the ## Executive Summary section (2-3 sentences).
                Focus on: number of demand forecasts generated today and in the last 7 days, \
                current inventory health status, out-of-stock SKU count, and whether \
                supply is aligned with forecasted demand. Flag critical gaps immediately.
                """.formatted(data));

        log.debug("[DEMAND_FORECAST] Generating section 2/4: Key Metrics");
        String s2 = reportAiService.generateSection("""
                You are writing the Key Metrics table for a Demand Forecast Report.

                DATA:
                %s

                Write ONLY the ## Key Metrics section.
                Produce a markdown table: Metric | Value | Status
                Include:
                - Forecasts Generated Today
                - Forecasts Generated (Last 7 Days)
                - Forecast Date
                - Inventory Health Status
                - Out-of-Stock SKUs
                - SKUs Below Reorder Level
                - Total Problematic SKUs
                - Recent Delivered Orders
                - Recent Revenue (KES)
                Status = ✅ Good / ⚠ Watch / 🔴 Critical.
                """.formatted(data));

        log.debug("[DEMAND_FORECAST] Generating section 3/4: Analysis");
        String s3 = reportAiService.generateSection("""
                You are writing the Analysis section for a Demand Forecast Report.

                DATA:
                %s

                Write ONLY the ## Analysis section with these sub-sections:
                ### Forecast Coverage
                Are forecasts being generated consistently? Is today's forecast count \
                aligned with the 7-day average? Gaps in forecast coverage mean blind spots in procurement.

                ### Demand vs Supply Alignment
                Compare forecasted demand signals with actual inventory health. \
                Where are the critical supply gaps (out-of-stock, below reorder level)? \
                What is the risk to order fulfilment?

                ### Sales Trend Context
                How do recent delivered orders and revenue inform demand expectations? \
                Is demand growing, stable, or declining?

                Be analytical. Identify the top 2-3 supply risks.
                """.formatted(data));

        log.debug("[DEMAND_FORECAST] Generating section 4/4: Recommendations");
        String s4 = reportAiService.generateSection("""
                You are writing the Recommendations section for a Demand Forecast Report.

                DATA:
                %s

                Write ONLY the ## Recommendations section.
                Provide exactly 5 numbered recommendations. Each must:
                - Start with a **bold action phrase**
                - Be specific to procurement, restocking, or forecast accuracy
                Focus on: urgently restocking out-of-stock SKUs, triggering reorders for \
                low-stock items, improving forecast generation frequency, aligning procurement \
                cycles with demand patterns, and reducing stockout risk for high-velocity SKUs.
                """.formatted(data));

        return new String[]{s1, s2, s3, s4};
    }

    // ── ANOMALY SUMMARY ───────────────────────────────────────────────────────

    private String[] buildAnomalySummarySections(String data, String periodDays) {
        log.debug("[ANOMALY_SUMMARY] Generating section 1/4: Executive Summary");
        String s1 = reportAiService.generateSection("""
                You are writing the Executive Summary for an Anomaly & Alerts Summary Report for a Kenyan FMCG distributor.

                DATA:
                %s

                Write ONLY the ## Executive Summary section (2-3 sentences).
                Focus on: total open alerts, breakdown by severity (Critical/High/Medium/Low), \
                most urgent alert types, and whether the anomaly level represents an operational \
                emergency or routine monitoring. Be direct — name the most critical issues.
                """.formatted(data));

        log.debug("[ANOMALY_SUMMARY] Generating section 2/4: Key Metrics");
        String s2 = reportAiService.generateSection("""
                You are writing the Key Metrics table for an Anomaly & Alerts Summary Report.

                DATA:
                %s

                Write ONLY the ## Key Metrics section.
                Produce TWO tables:

                Table 1 — Alert Severity Breakdown: Severity | Count | Status
                Rows: Critical | High | Medium | Low | Total Open
                Status = 🔴 Critical / 🟠 High / 🟡 Medium / 🟢 Low

                Table 2 — Recent Alerts: Alert Type | Severity | Date | Description
                List the 5 most recent alerts from the data (if available).
                """.formatted(data));

        log.debug("[ANOMALY_SUMMARY] Generating section 3/4: Analysis");
        String s3 = reportAiService.generateSection("""
                You are writing the Analysis section for an Anomaly & Alerts Summary Report covering the last %s days.

                DATA:
                %s

                Write ONLY the ## Analysis section with these sub-sections:
                ### Alert Severity & Urgency
                Interpret the distribution of alerts by severity. \
                Is the operation under critical stress, or are most alerts low-priority noise?

                ### Inventory & Shrinkage Anomalies
                Cross-reference inventory health with anomaly alerts. \
                Are stock anomalies contributing to the alert count? What is the potential shrinkage exposure?

                ### Payment & Financial Anomalies
                Are payment anomalies appearing? Cross-reference overdue orders and outstanding amounts. \
                Is there a financial integrity risk?

                Name specific alert types from the data. Prioritise by business impact.
                """.formatted(periodDays, data));

        log.debug("[ANOMALY_SUMMARY] Generating section 4/4: Recommendations");
        String s4 = reportAiService.generateSection("""
                You are writing the Recommendations section for an Anomaly & Alerts Summary Report.

                DATA:
                %s

                Write ONLY the ## Recommendations section.
                Provide exactly 5 numbered recommendations. Each must:
                - Start with a **bold action phrase**
                - Target a specific alert type or operational area
                Focus on: immediately escalating and resolving Critical alerts, \
                investigating High-severity inventory and payment anomalies, \
                establishing a daily alert review process, setting up automated escalation \
                for unresolved Critical alerts, and improving data quality to reduce false positives.
                """.formatted(data));

        return new String[]{s1, s2, s3, s4};
    }

    // ── Data assembly ─────────────────────────────────────────────────────────

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

    // ── BALANCE SHEET ─────────────────────────────────────────────────────────

    private String[] buildBalanceSheetSections(String data) {
        String s1 = reportAiService.generateSection("""
                You are writing the Executive Summary for a Balance Sheet report for a Kenyan FMCG distributor.

                DATA:
                %s

                Write ONLY the ## Executive Summary section (2-3 sentences).
                Focus on: total assets, total liabilities, total equity, whether the sheet is balanced, \
                and the overall financial health of the business. Flag any imbalance immediately. \
                Be direct and executive-ready.
                """.formatted(data));

        String s2 = reportAiService.generateSection("""
                You are writing the Key Metrics table for a Balance Sheet report.

                DATA:
                %s

                Write ONLY the ## Key Metrics section.
                Produce a markdown table: Metric | Value | Status
                Include:
                - Total Assets (KES)
                - Total Liabilities (KES)
                - Total Equity (KES)
                - Total Liabilities + Equity (KES)
                - Balance Sheet Balanced?
                - Top Asset Account (name + balance)
                - Top Liability Account (name + balance)
                Status = ✅ Good / ⚠ Watch / 🔴 Critical. Flag if not balanced.
                """.formatted(data));

        String s3 = reportAiService.generateSection("""
                You are writing the Analysis section for a Balance Sheet report.

                DATA:
                %s

                Write ONLY the ## Analysis section with these sub-sections:
                ### Asset Quality
                What assets does the business hold? Are they primarily current (liquid) or fixed (long-term)? \
                Is the asset base adequate to support operations?

                ### Liability Structure
                What are the major liabilities? Is leverage (debt ratio) at a manageable level? \
                Are liabilities short-term or long-term?

                ### Equity & Financial Strength
                What does the equity position indicate? Is the business funded primarily by equity or debt? \
                Is retained earnings positive?

                Use specific KES figures. Be analytical and concise.
                """.formatted(data));

        String s4 = reportAiService.generateSection("""
                You are writing the Recommendations section for a Balance Sheet report.

                DATA:
                %s

                Write ONLY the ## Recommendations section.
                Provide exactly 5 numbered recommendations. Each must:
                - Start with a **bold action phrase**
                - Address a specific balance sheet risk or opportunity
                Focus on: improving asset liquidity, reducing high-cost liabilities, strengthening equity, \
                addressing any imbalance, and optimising the asset mix.
                """.formatted(data));

        return new String[]{s1, s2, s3, s4};
    }

    // ── PROFIT & LOSS ─────────────────────────────────────────────────────────

    private String[] buildProfitLossSections(String data, String periodDays) {
        String s1 = reportAiService.generateSection("""
                You are writing the Executive Summary for a Profit & Loss Statement for a Kenyan FMCG distributor.

                DATA:
                %s

                Write ONLY the ## Executive Summary section (2-3 sentences).
                Focus on: total revenue (KES), gross profit, net income, gross margin (%%), \
                and whether the business is profitable. Flag losses immediately. Be direct.
                """.formatted(data));

        String s2 = reportAiService.generateSection("""
                You are writing the Key Metrics table for a Profit & Loss Statement.

                DATA:
                %s

                Write ONLY the ## Key Metrics section.
                Produce a markdown table: Metric | Value (KES) | Status
                Include:
                - Total Revenue
                - Cost of Goods Sold (COGS)
                - Gross Profit
                - Gross Margin (%%)
                - Total Operating Expenses
                - Net Income
                - Net Margin (%%)
                Status = ✅ Profitable / ⚠ Breakeven / 🔴 Loss. Calculate margins from the data.
                """.formatted(data));

        String s3 = reportAiService.generateSection("""
                You are writing the Analysis section for a Profit & Loss Statement covering the last %s days.

                DATA:
                %s

                Write ONLY the ## Analysis section with these sub-sections:
                ### Revenue & Gross Profit
                Is revenue growing or stable? What is the gross margin? Is COGS well controlled?

                ### Operating Expenses
                What are the major expense drivers? Are expenses proportionate to revenue? \
                Are there any unusually large expense lines?

                ### Profitability Assessment
                Is the business generating net profit? If not, what is driving the loss? \
                How does this period compare to expectations?

                Use specific KES figures. Be direct and analytical.
                """.formatted(periodDays, data));

        String s4 = reportAiService.generateSection("""
                You are writing the Recommendations section for a Profit & Loss Statement.

                DATA:
                %s

                Write ONLY the ## Recommendations section.
                Provide exactly 5 numbered recommendations. Each must:
                - Start with a **bold action phrase**
                - Target a specific revenue, cost, or margin improvement
                Focus on: growing revenue, reducing COGS through better procurement, \
                cutting non-essential expenses, improving gross margin, and accelerating path to profitability.
                """.formatted(data));

        return new String[]{s1, s2, s3, s4};
    }

    // ── TRIAL BALANCE ─────────────────────────────────────────────────────────

    private String[] buildTrialBalanceSections(String data) {
        String s1 = reportAiService.generateSection("""
                You are writing the Executive Summary for a Trial Balance for a Kenyan FMCG distributor.

                DATA:
                %s

                Write ONLY the ## Executive Summary section (2-3 sentences).
                Focus on: the accounting period, total debits, total credits, whether debits equal credits \
                (balanced), and if unbalanced — the difference amount. Flag any imbalance as a critical issue. \
                Be direct.
                """.formatted(data));

        String s2 = reportAiService.generateSection("""
                You are writing the Key Metrics table for a Trial Balance.

                DATA:
                %s

                Write ONLY the ## Key Metrics section.
                Produce a markdown table: Metric | Value
                Include:
                - Period Name
                - As Of Date
                - Total Debits (KES)
                - Total Credits (KES)
                - Difference (KES) — should be 0 if balanced
                - Balanced?
                - Number of Accounts
                Then produce a second table: Account Type | Total Debit | Total Credit
                Group accounts by type (ASSET, LIABILITY, EQUITY, REVENUE, EXPENSE) and sum each.
                """.formatted(data));

        String s3 = reportAiService.generateSection("""
                You are writing the Analysis section for a Trial Balance.

                DATA:
                %s

                Write ONLY the ## Analysis section with these sub-sections:
                ### Balance Status
                Is the trial balance in balance? If not, what is the discrepancy and what could cause it \
                (missing entries, posting errors, unposted transactions)?

                ### Account Balance Review
                Are there any accounts with unusual balances (e.g. a revenue account with a debit balance, \
                or an expense account with a credit balance)? Identify the 2-3 most significant accounts.

                ### Period Close Readiness
                Based on the trial balance, is the period ready to be closed? \
                Are there obvious posting gaps or errors to resolve first?

                Be specific with account names and amounts from the data.
                """.formatted(data));

        String s4 = reportAiService.generateSection("""
                You are writing the Recommendations section for a Trial Balance.

                DATA:
                %s

                Write ONLY the ## Recommendations section.
                Provide exactly 5 numbered recommendations. Each must:
                - Start with a **bold action phrase**
                - Address a specific accounting accuracy or period-close issue
                Focus on: investigating any imbalance, reviewing unusual account balances, \
                ensuring all transactions are posted, reconciling key accounts before period close, \
                and improving journal entry quality.
                """.formatted(data));

        return new String[]{s1, s2, s3, s4};
    }

    // ── CASH FLOW ────────────────────────────────────────────────────────────

    private String[] buildCashFlowSections(String data, String periodDays) {
        String s1 = reportAiService.generateSection("""
                You are writing the Executive Summary for a Cash Flow Statement for a Kenyan FMCG distributor.

                DATA:
                %s

                Write ONLY the ## Executive Summary section (2-3 sentences).
                Focus on: net cash change (positive = cash generated, negative = cash consumed), \
                operating cash flow direction, and whether the business is generating or burning cash. \
                Flag negative operating cash flow as a critical concern. Be direct.
                """.formatted(data));

        String s2 = reportAiService.generateSection("""
                You are writing the Key Metrics table for a Cash Flow Statement.

                DATA:
                %s

                Write ONLY the ## Key Metrics section.
                Produce a markdown table: Activity | Cash Flow (KES) | Status
                Include:
                - Operating Activities
                - Investing Activities
                - Financing Activities
                - Net Cash Change
                Status = ✅ Positive / 🔴 Negative / ⚠ Near Zero.
                Also list the top 3 individual line items by absolute amount.
                """.formatted(data));

        String s3 = reportAiService.generateSection("""
                You are writing the Analysis section for a Cash Flow Statement covering the last %s days.

                DATA:
                %s

                Write ONLY the ## Analysis section with these sub-sections:
                ### Operating Cash Flow
                Is the core business generating or consuming cash? What are the main operating drivers \
                (collections from customers, payments to suppliers, operating expenses)?

                ### Investing & Financing
                Is the business investing in growth (negative investing cash flow) or liquidating assets? \
                Is it drawing down debt or repaying it?

                ### Liquidity Position
                Taking all three sections together, is the business's cash position improving or deteriorating? \
                What is the risk to near-term liquidity?

                Use specific KES figures from the data.
                """.formatted(periodDays, data));

        String s4 = reportAiService.generateSection("""
                You are writing the Recommendations section for a Cash Flow Statement.

                DATA:
                %s

                Write ONLY the ## Recommendations section.
                Provide exactly 5 numbered recommendations. Each must:
                - Start with a **bold action phrase**
                - Target a specific cash flow improvement
                Focus on: accelerating collections to improve operating cash flow, \
                deferring non-essential capital expenditure, optimising supplier payment terms, \
                reducing cash burn in loss-making areas, and maintaining adequate cash reserves.
                """.formatted(data));

        return new String[]{s1, s2, s3, s4};
    }

    // ── AR AGING ─────────────────────────────────────────────────────────────

    private String[] buildArAgingSections(String data) {
        String s1 = reportAiService.generateSection("""
                You are writing the Executive Summary for an Accounts Receivable Aging report \
                for a Kenyan FMCG distributor.

                DATA:
                %s

                Write ONLY the ## Executive Summary section (2-3 sentences).
                Focus on: total outstanding receivables (KES), proportion that is current vs overdue, \
                the amount in the 90+ days bucket, and whether the collection situation is under control \
                or at a critical level. Be direct.
                """.formatted(data));

        String s2 = reportAiService.generateSection("""
                You are writing the Key Metrics table for an AR Aging report.

                DATA:
                %s

                Write ONLY the ## Key Metrics section.
                Produce a markdown table: Aging Bucket | Amount (KES) | %% of Total | Status
                Rows:
                - Current (not yet due)
                - 1 – 30 days overdue
                - 31 – 60 days overdue
                - 61 – 90 days overdue
                - 90+ days overdue
                - **Total Outstanding**
                Status = ✅ Current / ⚠ Watch / 🔴 Critical. \
                Flag anything 90+ days as critical.
                Then list the top 5 overdue customers from the data with their balances.
                """.formatted(data));

        String s3 = reportAiService.generateSection("""
                You are writing the Analysis section for an AR Aging report.

                DATA:
                %s

                Write ONLY the ## Analysis section with these sub-sections:
                ### Collection Efficiency
                What proportion of receivables are current vs overdue? \
                Is the collection cycle healthy (most amounts current) or deteriorating (large overdue buckets)?

                ### High-Risk Receivables
                Focus on the 61-90 day and 90+ day buckets. Name the top overdue customers. \
                What is the risk that these become bad debts?

                ### Impact on Cash Flow
                How does the overdue AR balance affect the distributor's cash position and ability to pay suppliers?

                Reference specific KES amounts and customer names.
                """.formatted(data));

        String s4 = reportAiService.generateSection("""
                You are writing the Recommendations section for an AR Aging report.

                DATA:
                %s

                Write ONLY the ## Recommendations section.
                Provide exactly 5 numbered recommendations. Each must:
                - Start with a **bold action phrase**
                - Target a specific customer group or collection action
                Focus on: immediately escalating 90+ day accounts for legal action or write-off review, \
                calling 61-90 day customers to arrange payment plans, reviewing credit terms for repeat offenders, \
                incentivising early payment, and improving invoice-to-collection cycle time.
                """.formatted(data));

        return new String[]{s1, s2, s3, s4};
    }

    // ── AP AGING ─────────────────────────────────────────────────────────────

    private String[] buildApAgingSections(String data) {
        String s1 = reportAiService.generateSection("""
                You are writing the Executive Summary for an Accounts Payable Aging report \
                for a Kenyan FMCG distributor.

                DATA:
                %s

                Write ONLY the ## Executive Summary section (2-3 sentences).
                Focus on: total outstanding payables (KES), proportion current vs overdue, \
                overdue supplier obligations, and whether delayed payments risk supplier relationships \
                or supply chain disruption. Be direct.
                """.formatted(data));

        String s2 = reportAiService.generateSection("""
                You are writing the Key Metrics table for an AP Aging report.

                DATA:
                %s

                Write ONLY the ## Key Metrics section.
                Produce a markdown table: Aging Bucket | Amount (KES) | %% of Total | Status
                Rows:
                - Current (not yet due)
                - 1 – 30 days overdue
                - 31 – 60 days overdue
                - 61 – 90 days overdue
                - 90+ days overdue
                - **Total Payable**
                Flag anything 60+ days as a supplier relationship risk.
                Then list the top 5 overdue suppliers with amounts.
                """.formatted(data));

        String s3 = reportAiService.generateSection("""
                You are writing the Analysis section for an AP Aging report.

                DATA:
                %s

                Write ONLY the ## Analysis section with these sub-sections:
                ### Payment Obligations Overview
                Is the business paying suppliers on time? What proportion is current vs overdue? \
                Is the overdue trend worsening?

                ### Supplier Relationship Risk
                For 60+ day overdue balances — which suppliers are most at risk of stopping supply? \
                Name specific overdue suppliers from the data.

                ### Cash Management Implications
                Does the high AP balance indicate a cash flow problem? \
                Or is the business deliberately extending payment terms to manage liquidity?

                Use specific KES figures and supplier names.
                """.formatted(data));

        String s4 = reportAiService.generateSection("""
                You are writing the Recommendations section for an AP Aging report.

                DATA:
                %s

                Write ONLY the ## Recommendations section.
                Provide exactly 5 numbered recommendations. Each must:
                - Start with a **bold action phrase**
                - Target a specific supplier or payment obligation
                Focus on: immediately settling 90+ day overdue accounts, \
                negotiating payment plans for 60-90 day balances, \
                prioritising payments to key FMCG suppliers to protect supply chain, \
                improving cash reserves for timely supplier payments, \
                and reviewing purchasing terms to align with cash flow cycles.
                """.formatted(data));

        return new String[]{s1, s2, s3, s4};
    }
}
