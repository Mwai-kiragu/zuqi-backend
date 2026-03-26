package com.zuqi.ai.assistant;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zuqi.ai.agent.tools.*;
import com.zuqi.ai.assistant.tools.CreditSummaryTool;
import com.zuqi.ai.assistant.tools.DemandForecastSummaryTool;
import com.zuqi.ai.agent.tools.BalanceSheetTool;
import com.zuqi.ai.agent.tools.ProfitLossTool;
import com.zuqi.ai.agent.tools.TrialBalanceTool;
import com.zuqi.ai.agent.tools.CashFlowTool;
import com.zuqi.ai.agent.tools.ArAgingTool;
import com.zuqi.ai.agent.tools.ApAgingTool;
import com.zuqi.api.dto.assistant.ReportDataResponse;
import com.zuqi.domain.ai.ReportType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportDataService {

    private final SalesTrendTool          salesTrendTool;
    private final InventoryHealthTool     inventoryHealthTool;
    private final PaymentPerformanceTool  paymentPerformanceTool;
    private final RepPerformanceTool      repPerformanceTool;
    private final MerchantMetricsTool     merchantMetricsTool;
    private final AnomalyAlertsTool       anomalyAlertsTool;
    private final DeliveryMetricsTool     deliveryMetricsTool;
    private final CreditSummaryTool       creditSummaryTool;
    private final DemandForecastSummaryTool demandForecastSummaryTool;
    private final BalanceSheetTool          balanceSheetTool;
    private final ProfitLossTool            profitLossTool;
    private final TrialBalanceTool          trialBalanceTool;
    private final CashFlowTool              cashFlowTool;
    private final ArAgingTool               arAgingTool;
    private final ApAgingTool               apAgingTool;
    private final ExpiryRiskTool            expiryRiskTool;
    private final ReorderSuggestionTool     reorderSuggestionTool;
    private final ChurnRiskTool             churnRiskTool;
    private final CustomerHealthTool        customerHealthTool;
    private final SupplierRiskTool          supplierRiskTool;
    private final PriceTrendTool            priceTrendTool;
    private final CustomerSegmentTool       customerSegmentTool;
    private final ObjectMapper            objectMapper;

    public ReportDataResponse getReportData(UUID distributorId, ReportType type, int periodDays) {
        String distId = distributorId.toString();
        String days = String.valueOf(periodDays);
        Map<String, Object> data = new LinkedHashMap<>();

        switch (type) {
            case SALES -> {
                data.put("sales",     parse(salesTrendTool.getSalesTrend(distId, days)));
                data.put("merchants", parse(merchantMetricsTool.getMerchantMetrics(distId)));
            }
            case INVENTORY -> {
                data.put("inventory", parse(inventoryHealthTool.getInventoryHealth(distId)));
                data.put("anomalies", parse(anomalyAlertsTool.getAnomalyAlerts(distId, days)));
            }
            case PAYMENT -> {
                data.put("payments", parse(paymentPerformanceTool.getPaymentPerformance(distId)));
                data.put("sales",    parse(salesTrendTool.getSalesTrend(distId, days)));
            }
            case CREDIT_RISK -> {
                data.put("credit",    parse(creditSummaryTool.getCreditSummary(distId)));
                data.put("merchants", parse(merchantMetricsTool.getMerchantMetrics(distId)));
                data.put("anomalies", parse(anomalyAlertsTool.getAnomalyAlerts(distId, days)));
            }
            case REP_PERFORMANCE -> {
                data.put("repPerformance", parse(repPerformanceTool.getRepPerformance(distId)));
                data.put("sales",          parse(salesTrendTool.getSalesTrend(distId, days)));
                data.put("delivery",       parse(deliveryMetricsTool.getDeliveryMetrics(distId)));
            }
            case MERCHANT_SUMMARY -> {
                data.put("merchants", parse(merchantMetricsTool.getMerchantMetrics(distId)));
                data.put("sales",     parse(salesTrendTool.getSalesTrend(distId, days)));
                data.put("payments",  parse(paymentPerformanceTool.getPaymentPerformance(distId)));
                data.put("credit",    parse(creditSummaryTool.getCreditSummary(distId)));
            }
            case DEMAND_FORECAST -> {
                data.put("demand",    parse(demandForecastSummaryTool.getDemandForecastSummary(distId)));
                data.put("inventory", parse(inventoryHealthTool.getInventoryHealth(distId)));
                data.put("sales",     parse(salesTrendTool.getSalesTrend(distId, days)));
            }
            case ANOMALY_SUMMARY -> {
                data.put("anomalies", parse(anomalyAlertsTool.getAnomalyAlerts(distId, days)));
                data.put("inventory", parse(inventoryHealthTool.getInventoryHealth(distId)));
                data.put("payments",  parse(paymentPerformanceTool.getPaymentPerformance(distId)));
            }
            case BALANCE_SHEET -> {
                data.put("balanceSheet", parse(balanceSheetTool.getBalanceSheet(distId)));
            }
            case PROFIT_LOSS -> {
                data.put("profitLoss", parse(profitLossTool.getProfitLoss(distId, days)));
            }
            case TRIAL_BALANCE -> {
                data.put("trialBalance", parse(trialBalanceTool.getTrialBalance(distId)));
            }
            case CASH_FLOW -> {
                data.put("cashFlow", parse(cashFlowTool.getCashFlow(distId, days)));
            }
            case AR_AGING -> {
                data.put("arAging",   parse(arAgingTool.getArAging(distId)));
                data.put("payments",  parse(paymentPerformanceTool.getPaymentPerformance(distId)));
            }
            case AP_AGING -> {
                data.put("apAging",   parse(apAgingTool.getApAging(distId)));
            }
            case EXPIRY_RISK -> {
                data.put("expiryRisks", parse(expiryRiskTool.getExpiryRisks(distId)));
                data.put("inventory",   parse(inventoryHealthTool.getInventoryHealth(distId)));
            }
            case REORDER_SUGGESTIONS -> {
                data.put("reorderSuggestions", parse(reorderSuggestionTool.getReorderSuggestions(distId)));
                data.put("inventory",          parse(inventoryHealthTool.getInventoryHealth(distId)));
            }
            case CHURN_RISK -> {
                data.put("churnRisk", parse(churnRiskTool.getChurnRisk(distId)));
                data.put("merchants", parse(merchantMetricsTool.getMerchantMetrics(distId)));
            }
            case CUSTOMER_HEALTH -> {
                data.put("customerHealth",   parse(customerHealthTool.getCustomerHealth(distId)));
                data.put("customerSegments", parse(customerSegmentTool.getCustomerSegments(distId)));
            }
            case SUPPLIER_RISK -> {
                data.put("supplierRisk", parse(supplierRiskTool.getSupplierRisk(distId)));
            }
            case PRICE_TRENDS -> {
                data.put("priceTrends", parse(priceTrendTool.getPriceTrends(distId)));
                data.put("supplierRisk", parse(supplierRiskTool.getSupplierRisk(distId)));
            }
        }

        return ReportDataResponse.builder()
                .type(type)
                .distributorId(distributorId)
                .generatedAt(LocalDateTime.now())
                .periodDays(periodDays)
                .data(data)
                .build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parse(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse tool response: {}", e.getMessage());
            return Map.of("raw", json, "error", e.getMessage());
        }
    }
}
