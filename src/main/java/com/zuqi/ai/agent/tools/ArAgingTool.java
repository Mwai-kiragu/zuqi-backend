package com.zuqi.ai.agent.tools;

import com.zuqi.api.dto.aging.AgingBucketSummary;
import com.zuqi.api.dto.aging.ArAgingResponse;
import com.zuqi.api.dto.aging.ArAgingRow;
import com.zuqi.service.AgingReportService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class ArAgingTool {

    private final AgingReportService agingReportService;

    @Tool("Get accounts receivable (AR) aging report for a distributor as of today. " +
          "Returns total outstanding balance, amounts by aging bucket (current, 1-30, 31-60, " +
          "61-90, 90+ days), and the top overdue customers. Parameter: distributorId (UUID string).")
    public String getArAging(@P("The distributor UUID") String distributorId) {
        log.info("[TOOL CALLED] getArAging distributorId={}", distributorId);
        try {
            UUID distId = UUID.fromString(distributorId.trim());
            ArAgingResponse ar = agingReportService.getArAging(distId, LocalDate.now());

            AgingBucketSummary s = ar.getSummary();
            StringBuilder sb = new StringBuilder();
            sb.append(String.format(
                "{ \"tool\": \"ArAging\", \"asOfDate\": \"%s\", " +
                "\"totalOutstanding\": \"%s\", \"current\": \"%s\", " +
                "\"overdue1_30\": \"%s\", \"overdue31_60\": \"%s\", " +
                "\"overdue61_90\": \"%s\", \"overdue90plus\": \"%s\", " +
                "\"totalInvoices\": %d, ",
                ar.getAsOfDate(),
                s.getTotal().toPlainString(), s.getCurrent().toPlainString(),
                s.getBucket1().toPlainString(), s.getBucket2().toPlainString(),
                s.getBucket3().toPlainString(), s.getBucket4().toPlainString(),
                ar.getRows().size()));

            // Top 5 most overdue customers
            List<ArAgingRow> top5 = ar.getRows().stream()
                    .filter(r -> r.getDaysOverdue() > 0)
                    .sorted(Comparator.comparingLong(ArAgingRow::getDaysOverdue).reversed())
                    .limit(5)
                    .toList();

            sb.append("\"topOverdueCustomers\": [");
            for (int i = 0; i < top5.size(); i++) {
                if (i > 0) sb.append(", ");
                ArAgingRow r = top5.get(i);
                sb.append(String.format(
                    "{ \"customer\": \"%s\", \"balanceDue\": \"%s\", \"daysOverdue\": %d }",
                    r.getCustomerName().replace("\"", "'"),
                    r.getBalanceDue().toPlainString(),
                    r.getDaysOverdue()));
            }
            sb.append("] }");
            return sb.toString();

        } catch (IllegalArgumentException e) {
            log.error("ArAgingTool: invalid distributorId '{}'", distributorId, e);
            return "{ \"error\": \"Invalid distributorId: " + distributorId + "\" }";
        } catch (Exception e) {
            log.error("ArAgingTool: error for distributorId '{}'", distributorId, e);
            return "{ \"error\": \"Failed to retrieve AR aging: " + e.getMessage() + "\" }";
        }
    }
}
