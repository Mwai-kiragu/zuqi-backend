package com.zuqi.ai.agent.tools;

import com.zuqi.api.dto.gl.BalanceSheetResponse;
import com.zuqi.api.dto.gl.BalanceSheetRow;
import com.zuqi.service.GlReportService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class BalanceSheetTool {

    private final GlReportService glReportService;

    @Tool("Get the balance sheet for a distributor as of today. Returns total assets, " +
          "liabilities, equity, whether the sheet is balanced, and the top accounts " +
          "in each section. Parameter: distributorId (UUID string).")
    public String getBalanceSheet(@P("The distributor UUID") String distributorId) {
        log.info("[TOOL CALLED] getBalanceSheet distributorId={}", distributorId);
        try {
            UUID distId = UUID.fromString(distributorId.trim());
            BalanceSheetResponse bs = glReportService.getBalanceSheet(distId, LocalDate.now());

            BigDecimal totalAssets      = bs.getAssets().getTotal();
            BigDecimal totalLiabilities = bs.getLiabilities().getTotal();
            BigDecimal totalEquity      = bs.getEquity().getTotal();
            BigDecimal liabAndEquity    = bs.getTotalLiabilitiesAndEquity();
            boolean balanced = totalAssets.compareTo(liabAndEquity) == 0;

            StringBuilder sb = new StringBuilder();
            sb.append(String.format(
                "{ \"tool\": \"BalanceSheet\", \"asOfDate\": \"%s\", " +
                "\"totalAssets\": \"%s\", \"totalLiabilities\": \"%s\", " +
                "\"totalEquity\": \"%s\", \"totalLiabilitiesAndEquity\": \"%s\", " +
                "\"balanced\": %b, ",
                bs.getAssets() != null ? LocalDate.now() : "unknown",
                totalAssets.toPlainString(), totalLiabilities.toPlainString(),
                totalEquity.toPlainString(), liabAndEquity.toPlainString(), balanced));

            sb.append("\"topAssets\": ").append(rowsJson(bs.getAssets().getRows(), 5)).append(", ");
            sb.append("\"topLiabilities\": ").append(rowsJson(bs.getLiabilities().getRows(), 5)).append(", ");
            sb.append("\"topEquity\": ").append(rowsJson(bs.getEquity().getRows(), 5));
            sb.append(" }");
            return sb.toString();

        } catch (IllegalArgumentException e) {
            log.error("BalanceSheetTool: invalid distributorId '{}'", distributorId, e);
            return "{ \"error\": \"Invalid distributorId: " + distributorId + "\" }";
        } catch (Exception e) {
            log.error("BalanceSheetTool: error for distributorId '{}'", distributorId, e);
            return "{ \"error\": \"Failed to retrieve balance sheet: " + e.getMessage() + "\" }";
        }
    }

    private String rowsJson(List<BalanceSheetRow> rows, int limit) {
        if (rows == null || rows.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        int count = 0;
        for (BalanceSheetRow row : rows) {
            if (count >= limit) break;
            if (count > 0) sb.append(", ");
            sb.append(String.format("{ \"code\": \"%s\", \"name\": \"%s\", \"balance\": \"%s\" }",
                    row.getAccountCode(),
                    row.getAccountName().replace("\"", "'"),
                    row.getBalance().toPlainString()));
            count++;
        }
        return sb.append("]").toString();
    }
}
