package com.zuqi.ai.agent.tools;

import com.zuqi.api.dto.gl.CashFlowResponse;
import com.zuqi.api.dto.gl.CashFlowRow;
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
public class CashFlowTool {

    private final GlReportService glReportService;

    @Tool("Get the cash flow statement for a distributor. Returns operating, investing, " +
          "and financing cash flows with net cash change for the period. " +
          "Parameters: distributorId (UUID string), periodDays (look-back days, default 30).")
    public String getCashFlow(
            @P("The distributor UUID") String distributorId,
            @P("Look-back period in days") String periodDays) {
        log.info("[TOOL CALLED] getCashFlow distributorId={} periodDays={}", distributorId, periodDays);
        try {
            UUID distId = UUID.fromString(distributorId.trim());
            int days = parseDays(periodDays);
            LocalDate toDate   = LocalDate.now();
            LocalDate fromDate = toDate.minusDays(days);

            CashFlowResponse cf = glReportService.getCashFlowStatement(distId, fromDate, toDate);

            StringBuilder sb = new StringBuilder();
            sb.append(String.format(
                "{ \"tool\": \"CashFlow\", \"fromDate\": \"%s\", \"toDate\": \"%s\", " +
                "\"operatingTotal\": \"%s\", \"investingTotal\": \"%s\", " +
                "\"financingTotal\": \"%s\", \"netCashChange\": \"%s\", ",
                fromDate, toDate,
                cf.getOperatingActivities().getTotal().toPlainString(),
                cf.getInvestingActivities().getTotal().toPlainString(),
                cf.getFinancingActivities().getTotal().toPlainString(),
                cf.getNetCashChange().toPlainString()));

            sb.append("\"operatingItems\": ").append(cfRowsJson(cf.getOperatingActivities().getRows())).append(", ");
            sb.append("\"investingItems\": ").append(cfRowsJson(cf.getInvestingActivities().getRows())).append(", ");
            sb.append("\"financingItems\": ").append(cfRowsJson(cf.getFinancingActivities().getRows()));
            sb.append(" }");
            return sb.toString();

        } catch (IllegalArgumentException e) {
            log.error("CashFlowTool: invalid distributorId '{}'", distributorId, e);
            return "{ \"error\": \"Invalid distributorId: " + distributorId + "\" }";
        } catch (Exception e) {
            log.error("CashFlowTool: error for distributorId '{}'", distributorId, e);
            return "{ \"error\": \"Failed to retrieve cash flow: " + e.getMessage() + "\" }";
        }
    }

    private String cfRowsJson(List<CashFlowRow> rows) {
        if (rows == null || rows.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < rows.size(); i++) {
            if (i > 0) sb.append(", ");
            CashFlowRow r = rows.get(i);
            sb.append(String.format("{ \"label\": \"%s\", \"amount\": \"%s\" }",
                    r.getLabel().replace("\"", "'"),
                    r.getAmount().toPlainString()));
        }
        return sb.append("]").toString();
    }

    private int parseDays(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 30; }
    }
}
