package com.zuqi.ai.assistant;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zuqi.ai.agent.tools.*;
import com.zuqi.ai.assistant.tools.CreditSummaryTool;
import com.zuqi.ai.assistant.tools.DemandForecastSummaryTool;
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
