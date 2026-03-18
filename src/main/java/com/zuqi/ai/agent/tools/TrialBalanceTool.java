package com.zuqi.ai.agent.tools;

import com.zuqi.api.dto.gl.TrialBalanceResponse;
import com.zuqi.api.dto.gl.TrialBalanceRow;
import com.zuqi.domain.gl.GlPeriod;
import com.zuqi.repository.GlPeriodRepository;
import com.zuqi.service.GlReportService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class TrialBalanceTool {

    private final GlReportService    glReportService;
    private final GlPeriodRepository glPeriodRepository;

    @Tool("Get the trial balance for a distributor using the most recent accounting period. " +
          "Returns total debits, total credits, balanced status, period name, and a summary " +
          "of account balances by type. Parameter: distributorId (UUID string).")
    public String getTrialBalance(@P("The distributor UUID") String distributorId) {
        log.info("[TOOL CALLED] getTrialBalance distributorId={}", distributorId);
        try {
            UUID distId = UUID.fromString(distributorId.trim());

            List<GlPeriod> periods = glPeriodRepository
                    .findByDistributorIdOrderByPeriodYearDescPeriodMonthDesc(distId);
            if (periods.isEmpty()) {
                return "{ \"tool\": \"TrialBalance\", \"error\": \"No accounting periods found\" }";
            }
            GlPeriod latest = periods.get(0);

            TrialBalanceResponse tb = glReportService.getTrialBalance(distId, latest.getId());
            boolean balanced = tb.getTotalDebits().compareTo(tb.getTotalCredits()) == 0;

            StringBuilder sb = new StringBuilder();
            sb.append(String.format(
                "{ \"tool\": \"TrialBalance\", \"periodName\": \"%s\", \"asOfDate\": \"%s\", " +
                "\"totalDebits\": \"%s\", \"totalCredits\": \"%s\", \"balanced\": %b, ",
                tb.getPeriodName(), tb.getAsOfDate(),
                tb.getTotalDebits().toPlainString(),
                tb.getTotalCredits().toPlainString(),
                balanced));

            // Group by account type and include key rows
            sb.append("\"accounts\": [");
            List<TrialBalanceRow> rows = tb.getRows();
            int limit = Math.min(rows.size(), 30);
            for (int i = 0; i < limit; i++) {
                if (i > 0) sb.append(", ");
                TrialBalanceRow r = rows.get(i);
                BigDecimal debit  = r.getClosingDebit()  != null ? r.getClosingDebit()  : BigDecimal.ZERO;
                BigDecimal credit = r.getClosingCredit() != null ? r.getClosingCredit() : BigDecimal.ZERO;
                sb.append(String.format(
                    "{ \"code\": \"%s\", \"name\": \"%s\", \"type\": \"%s\", " +
                    "\"debit\": \"%s\", \"credit\": \"%s\" }",
                    r.getAccountCode(),
                    r.getAccountName().replace("\"", "'"),
                    r.getAccountType(),
                    debit.toPlainString(),
                    credit.toPlainString()));
            }
            sb.append("] }");
            return sb.toString();

        } catch (IllegalArgumentException e) {
            log.error("TrialBalanceTool: invalid distributorId '{}'", distributorId, e);
            return "{ \"error\": \"Invalid distributorId: " + distributorId + "\" }";
        } catch (Exception e) {
            log.error("TrialBalanceTool: error for distributorId '{}'", distributorId, e);
            return "{ \"error\": \"Failed to retrieve trial balance: " + e.getMessage() + "\" }";
        }
    }
}
