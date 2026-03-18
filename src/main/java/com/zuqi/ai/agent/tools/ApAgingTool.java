package com.zuqi.ai.agent.tools;

import com.zuqi.api.dto.aging.AgingBucketSummary;
import com.zuqi.api.dto.aging.ApAgingResponse;
import com.zuqi.api.dto.aging.ApAgingRow;
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
public class ApAgingTool {

    private final AgingReportService agingReportService;

    @Tool("Get accounts payable (AP) aging report for a distributor as of today. " +
          "Returns total payable balance, amounts by aging bucket (current, 1-30, 31-60, " +
          "61-90, 90+ days), and the top overdue suppliers. Parameter: distributorId (UUID string).")
    public String getApAging(@P("The distributor UUID") String distributorId) {
        log.info("[TOOL CALLED] getApAging distributorId={}", distributorId);
        try {
            UUID distId = UUID.fromString(distributorId.trim());
            ApAgingResponse ap = agingReportService.getApAging(distId, LocalDate.now());

            AgingBucketSummary s = ap.getSummary();
            StringBuilder sb = new StringBuilder();
            sb.append(String.format(
                "{ \"tool\": \"ApAging\", \"asOfDate\": \"%s\", " +
                "\"totalPayable\": \"%s\", \"current\": \"%s\", " +
                "\"overdue1_30\": \"%s\", \"overdue31_60\": \"%s\", " +
                "\"overdue61_90\": \"%s\", \"overdue90plus\": \"%s\", " +
                "\"totalBills\": %d, ",
                ap.getAsOfDate(),
                s.getTotal().toPlainString(), s.getCurrent().toPlainString(),
                s.getBucket1().toPlainString(), s.getBucket2().toPlainString(),
                s.getBucket3().toPlainString(), s.getBucket4().toPlainString(),
                ap.getRows().size()));

            // Top 5 most overdue suppliers
            List<ApAgingRow> top5 = ap.getRows().stream()
                    .filter(r -> r.getDaysOverdue() > 0)
                    .sorted(Comparator.comparingLong(ApAgingRow::getDaysOverdue).reversed())
                    .limit(5)
                    .toList();

            sb.append("\"topOverdueSuppliers\": [");
            for (int i = 0; i < top5.size(); i++) {
                if (i > 0) sb.append(", ");
                ApAgingRow r = top5.get(i);
                sb.append(String.format(
                    "{ \"supplier\": \"%s\", \"amount\": \"%s\", \"daysOverdue\": %d }",
                    r.getSupplierName().replace("\"", "'"),
                    r.getTotalAmount().toPlainString(),
                    r.getDaysOverdue()));
            }
            sb.append("] }");
            return sb.toString();

        } catch (IllegalArgumentException e) {
            log.error("ApAgingTool: invalid distributorId '{}'", distributorId, e);
            return "{ \"error\": \"Invalid distributorId: " + distributorId + "\" }";
        } catch (Exception e) {
            log.error("ApAgingTool: error for distributorId '{}'", distributorId, e);
            return "{ \"error\": \"Failed to retrieve AP aging: " + e.getMessage() + "\" }";
        }
    }
}
