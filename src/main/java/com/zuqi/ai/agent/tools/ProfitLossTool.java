package com.zuqi.ai.agent.tools;

import com.zuqi.api.dto.gl.ProfitLossResponse;
import com.zuqi.api.dto.gl.ProfitLossRow;
import com.zuqi.service.GlReportService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProfitLossTool {

    private final GlReportService glReportService;

    @Tool("Get the Profit & Loss (income statement) for a distributor. Returns total revenue, " +
          "cost of goods sold, gross profit, operating expenses, and net income for the period. " +
          "Parameters: distributorId (UUID string), periodDays (number of days to look back, default 30).")
    public String getProfitLoss(
            @P("The distributor UUID") String distributorId,
            @P("Look-back period in days") String periodDays) {
        log.info("[TOOL CALLED] getProfitLoss distributorId={} periodDays={}", distributorId, periodDays);
        try {
            UUID distId = UUID.fromString(distributorId.trim());
            int days = parseDays(periodDays);
            LocalDate toDate   = LocalDate.now();
            LocalDate fromDate = toDate.minusDays(days);

            ProfitLossResponse pl = glReportService.getProfitAndLoss(distId, fromDate, toDate);

            StringBuilder sb = new StringBuilder();
            sb.append(String.format(
                "{ \"tool\": \"ProfitLoss\", \"fromDate\": \"%s\", \"toDate\": \"%s\", " +
                "\"totalRevenue\": \"%s\", \"totalCogs\": \"%s\", " +
                "\"grossProfit\": \"%s\", \"totalExpenses\": \"%s\", \"netIncome\": \"%s\", ",
                fromDate, toDate,
                pl.getRevenue().getTotal().toPlainString(),
                pl.getCostOfGoods().getTotal().toPlainString(),
                pl.getGrossProfit().toPlainString(),
                pl.getExpenses().getTotal().toPlainString(),
                pl.getNetIncome().toPlainString()));

            sb.append("\"revenueAccounts\": ").append(plRowsJson(pl.getRevenue().getRows(), 5)).append(", ");
            sb.append("\"expenseAccounts\": ").append(plRowsJson(pl.getExpenses().getRows(), 5));
            sb.append(" }");
            return sb.toString();

        } catch (IllegalArgumentException e) {
            log.error("ProfitLossTool: invalid distributorId '{}'", distributorId, e);
            return "{ \"error\": \"Invalid distributorId: " + distributorId + "\" }";
        } catch (Exception e) {
            log.error("ProfitLossTool: error for distributorId '{}'", distributorId, e);
            return "{ \"error\": \"Failed to retrieve P&L: " + e.getMessage() + "\" }";
        }
    }

    private String plRowsJson(List<ProfitLossRow> rows, int limit) {
        if (rows == null || rows.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        int count = 0;
        for (ProfitLossRow row : rows) {
            if (count >= limit) break;
            if (count > 0) sb.append(", ");
            sb.append(String.format("{ \"code\": \"%s\", \"name\": \"%s\", \"amount\": \"%s\" }",
                    row.getAccountCode(),
                    row.getAccountName().replace("\"", "'"),
                    row.getAmount().toPlainString()));
            count++;
        }
        return sb.append("]").toString();
    }

    private int parseDays(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 30; }
    }
}
