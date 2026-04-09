package com.zuqi.ai.agent.tools;

import com.zuqi.ai.reporting.ComplianceReportJob;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class ComplianceReportTool {

    private final ComplianceReportJob complianceReportJob;

    @Tool("Generate or retrieve a compliance report for a distributor. When a principal is specified " +
          "(e.g. 'UNILEVER', 'PG', 'EABL', 'DEFAULT'), generates a principal-specific report; " +
          "otherwise generates the default report. The report is an LLM-generated Markdown narrative " +
          "covering sales performance, order fulfilment, payment collection, and distributor health " +
          "for the previous calendar month. Suitable for sharing with principal manufacturers. " +
          "Note: generation takes 15–30 seconds.")
    public String generateComplianceReport(
            @P("The distributor UUID") String distributorId,
            @P("Optional: principal name — 'UNILEVER', 'PG', 'EABL', or leave empty for default report.") String principal) {

        log.info("[TOOL CALLED] generateComplianceReport distributorId={} principal={}", distributorId, principal);
        try {
            UUID distId = UUID.fromString(distributorId.trim());

            String report;
            if (principal != null && !principal.isBlank()) {
                report = complianceReportJob.generateForDistributorAndPrincipal(distId, principal.trim().toUpperCase());
            } else {
                report = complianceReportJob.generateForDistributor(distId);
            }

            // Truncate for agent context — full report is available via REST /v1/ai/reports/compliance
            String preview = report.length() > 2000
                    ? report.substring(0, 2000) + "\n...[truncated — full report available via REST API]"
                    : report;

            return "{ \"tool\": \"ComplianceReport\", \"distributorId\": \"" + distId + "\", "
                    + "\"principal\": \"" + (principal != null ? principal : "DEFAULT") + "\", "
                    + "\"report\": \"" + preview.replace("\"", "'").replace("\n", "\\n") + "\" }";

        } catch (IllegalArgumentException e) {
            log.error("ComplianceReportTool: invalid distributorId '{}'", distributorId, e);
            return "{ \"error\": \"Invalid distributorId format: " + distributorId + "\" }";
        } catch (Exception e) {
            log.error("ComplianceReportTool: report generation failed for distributorId '{}'", distributorId, e);
            return "{ \"error\": \"Compliance report generation failed: " + e.getMessage() + "\" }";
        }
    }
}
