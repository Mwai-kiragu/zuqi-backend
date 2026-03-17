package com.zuqi.ai.assistant.tools;

import com.zuqi.repository.DemandForecastRepository;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

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
          "generated for today), forecastsThisWeek (last 7 days), forecastDate (today's date). " +
          "Use this tool to answer questions about demand predictions and order suggestions. " +
          "Parameter: distributorId (UUID string).")
    @Transactional(readOnly = true)
    public String getDemandForecastSummary(@P("The distributor UUID") String distributorId) {
        log.info("[TOOL CALLED] getDemandForecastSummary distributorId={}", distributorId);
        try {
            UUID distId = UUID.fromString(distributorId.trim());

            LocalDate today = LocalDate.now();
            long forecastsToday = demandForecastRepository
                    .countByDistributorIdAndForecastDate(distId, today);

            // Count forecasts for this week (last 7 days)
            long forecastsWeek = 0;
            for (int i = 0; i < 7; i++) {
                forecastsWeek += demandForecastRepository
                        .countByDistributorIdAndForecastDate(distId, today.minusDays(i));
            }

            return String.format(
                    "{ \"tool\": \"DemandForecastSummary\", \"distributorId\": \"%s\", " +
                    "\"forecastDate\": \"%s\", \"forecastsToday\": %d, \"forecastsLast7Days\": %d }",
                    distId, today, forecastsToday, forecastsWeek);

        } catch (IllegalArgumentException e) {
            log.error("DemandForecastSummaryTool: invalid distributorId '{}'", distributorId, e);
            return "{ \"error\": \"Invalid distributorId: " + distributorId + "\" }";
        } catch (Exception e) {
            log.error("DemandForecastSummaryTool: error for distributorId '{}'", distributorId, e);
            return "{ \"error\": \"Failed to retrieve forecast summary: " + e.getMessage() + "\" }";
        }
    }
}
