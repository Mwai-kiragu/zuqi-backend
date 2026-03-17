package com.zuqi.ai.assistant.tools;

import com.zuqi.domain.ai.DemandForecast;
import com.zuqi.repository.DemandForecastRepository;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * LangChain4j tool that retrieves demand forecast summary data for a distributor.
 * Used by AssistantAgent to answer demand/forecast questions and build DEMAND_FORECAST reports.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DemandForecastSummaryTool {

    private final DemandForecastRepository demandForecastRepository;

    @Tool("Get demand forecast summary for a distributor. Returns: forecastsToday (count of forecasts " +
          "generated for today), forecastsLast7Days, forecastDate, and the top 5 highest-demand " +
          "forecasts for today with product name, merchant name, and predicted quantity. " +
          "Use this tool to answer questions about demand predictions and order suggestions. " +
          "Parameter: distributorId (UUID string).")
    @Transactional(readOnly = true)
    public String getDemandForecastSummary(@P("The distributor UUID") String distributorId) {
        log.info("[TOOL CALLED] getDemandForecastSummary distributorId={}", distributorId);
        try {
            UUID distId = UUID.fromString(distributorId.trim());

            LocalDate today = LocalDate.now();
            List<DemandForecast> todayForecasts = demandForecastRepository
                    .findByDistributorIdAndForecastDate(distId, today);
            long forecastsToday = todayForecasts.size();

            // Count forecasts for last 7 days
            long forecastsWeek = forecastsToday;
            for (int i = 1; i < 7; i++) {
                forecastsWeek += demandForecastRepository
                        .countByDistributorIdAndForecastDate(distId, today.minusDays(i));
            }

            // Top 5 highest predicted quantity for today
            List<DemandForecast> top5 = todayForecasts.stream()
                    .filter(f -> f.getPredictedQty() != null)
                    .sorted(Comparator.comparingDouble(DemandForecast::getPredictedQty).reversed())
                    .limit(5)
                    .collect(Collectors.toList());

            StringBuilder sb = new StringBuilder();
            sb.append(String.format(
                    "{ \"tool\": \"DemandForecastSummary\", \"distributorId\": \"%s\", " +
                    "\"forecastDate\": \"%s\", \"forecastsToday\": %d, \"forecastsLast7Days\": %d, ",
                    distId, today, forecastsToday, forecastsWeek));

            sb.append("\"topForecastsToday\": [");
            for (int i = 0; i < top5.size(); i++) {
                DemandForecast f = top5.get(i);
                String product  = f.getSku() != null ? f.getSku().getName().replace("\"", "'") : "Unknown";
                String merchant = f.getMerchant() != null ? f.getMerchant().getBusinessName().replace("\"", "'") : "Unknown";
                String qty      = String.format("%.2f", f.getPredictedQty());
                sb.append(String.format(
                        "{ \"product\": \"%s\", \"merchant\": \"%s\", \"predictedQty\": %s }",
                        product, merchant, qty));
                if (i < top5.size() - 1) sb.append(", ");
            }
            sb.append("] }");

            return sb.toString();

        } catch (IllegalArgumentException e) {
            log.error("DemandForecastSummaryTool: invalid distributorId '{}'", distributorId, e);
            return "{ \"error\": \"Invalid distributorId: " + distributorId + "\" }";
        } catch (Exception e) {
            log.error("DemandForecastSummaryTool: error for distributorId '{}'", distributorId, e);
            return "{ \"error\": \"Failed to retrieve forecast summary: " + e.getMessage() + "\" }";
        }
    }
}
