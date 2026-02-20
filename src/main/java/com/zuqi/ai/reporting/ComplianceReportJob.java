package com.zuqi.ai.reporting;

import com.zuqi.domain.distributor.Distributor;
import com.zuqi.repository.DistributorRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Scheduled service that drives monthly compliance report generation for all distributors.
 *
 * Runs on the 1st of every month at 04:00 EAT by default. The cron expression
 * can be overridden via the {@code zuqi.ai.reporting.compliance-cron} property.
 *
 * Metrics emitted:
 * <ul>
 *   <li>{@code zuqi_ai_compliance_reports_generated} — increment per successful report</li>
 *   <li>{@code zuqi_ai_compliance_reports_failed}    — increment per failed report</li>
 * </ul>
 *
 * Blueprint reference: implementation_plan.md Phase 6 Task 6.3
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ComplianceReportJob {

    private static final DateTimeFormatter PERIOD_FMT = DateTimeFormatter.ofPattern("MMMM yyyy");

    private final ComplianceReportAiService complianceReportAiService;
    private final ReportTemplateRegistry    reportTemplateRegistry;
    private final DistributorRepository     distributorRepository;
    private final MeterRegistry             meterRegistry;

    // ── Scheduled Entry Point ──────────────────────────────────────────────

    /**
     * Monthly compliance report generation for all active distributors.
     *
     * Scheduled at 04:00 on the 1st of every month (server time / EAT).
     * Each distributor's report is generated independently; errors are
     * caught per-distributor so that one failure does not block others.
     */
    @Scheduled(cron = "${zuqi.ai.reporting.compliance-cron:0 0 4 1 * *}")
    public void runMonthlyComplianceReports() {
        log.info("=== Starting monthly compliance report generation ===");
        long startTime = System.currentTimeMillis();

        List<Distributor> distributors = distributorRepository.findAll();
        log.info("Generating compliance reports for {} distributors", distributors.size());

        int success = 0;
        int failed  = 0;

        for (Distributor distributor : distributors) {
            try {
                String report = buildReportForDistributor(distributor, null);
                log.info("Generated compliance report for distributor={} ({} chars)",
                        distributor.getId(), report.length());

                successCounter().increment();
                success++;

            } catch (Exception e) {
                log.error("Compliance report generation failed for distributor={}: {}",
                        distributor.getId(), e.getMessage(), e);
                failedCounter().increment();
                failed++;
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("=== Monthly compliance reports complete: success={}, failed={}, duration={}ms ===",
                success, failed, duration);
    }

    // ── On-Demand Generation ───────────────────────────────────────────────

    /**
     * Generate a compliance report immediately for a single distributor.
     *
     * Used by {@link com.zuqi.api.controller.AiReportController} for on-demand
     * report requests via the REST API.
     *
     * @param distributorId UUID of the target distributor
     * @return Generated markdown report content
     * @throws IllegalArgumentException if the distributor is not found
     */
    public String generateForDistributor(UUID distributorId) {
        Distributor distributor = distributorRepository.findById(distributorId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Distributor not found: " + distributorId));

        try {
            String report = buildReportForDistributor(distributor, null);
            log.info("On-demand compliance report generated for distributor={}", distributorId);
            successCounter().increment();
            return report;
        } catch (Exception e) {
            log.error("On-demand compliance report failed for distributor={}: {}",
                    distributorId, e.getMessage(), e);
            failedCounter().increment();
            throw e;
        }
    }

    /**
     * Generate a compliance report for a specific distributor and principal.
     *
     * @param distributorId UUID of the target distributor
     * @param principal     Principal key (e.g. "UNILEVER"). Null uses DEFAULT template.
     * @return Generated markdown report content
     */
    public String generateForDistributorAndPrincipal(UUID distributorId, String principal) {
        Distributor distributor = distributorRepository.findById(distributorId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Distributor not found: " + distributorId));

        try {
            String report = buildReportForDistributor(distributor, principal);
            log.info("Compliance report generated for distributor={} principal={}", distributorId, principal);
            successCounter().increment();
            return report;
        } catch (Exception e) {
            log.error("Compliance report failed for distributor={} principal={}: {}",
                    distributorId, principal, e.getMessage(), e);
            failedCounter().increment();
            throw e;
        }
    }

    // ── Private Helpers ────────────────────────────────────────────────────

    /**
     * Build the LLM context string and invoke the AI service.
     *
     * The context is a structured prompt fragment that supplies the LLM with
     * all data it needs to fill in the report template. Real metric values would
     * be injected here by querying feature services; placeholder values are used
     * until Phase 6 feature queries are wired in.
     */
    private String buildReportForDistributor(Distributor distributor, String principalKey) {
        ReportTemplateRegistry.ReportTemplate template = reportTemplateRegistry.getTemplate(principalKey);

        String period    = LocalDate.now().minusMonths(1).format(PERIOD_FMT);
        String reportDate = LocalDate.now().toString();

        String context = String.format("""
                COMPLIANCE REPORT GENERATION REQUEST
                =====================================
                Distributor ID   : %s
                Distributor Name : %s
                Reporting Period : %s
                Report Date      : %s
                Principal        : %s

                REPORT TEMPLATE
                ---------------
                Sections        : %s
                Tone            : %s
                Required Metrics: %s

                OPERATIONAL DATA SUMMARY (placeholder — replace with live feature queries)
                ---------------------------------------------------------------------------
                - Total orders processed: data pending feature store integration
                - Order fulfilment rate: data pending feature store integration
                - On-time delivery rate: data pending feature store integration
                - Payment collection rate: data pending feature store integration
                - Active merchant count: data pending feature store integration
                - Stockout incidents: data pending feature store integration
                - Average credit utilisation: data pending feature store integration

                Please generate a complete compliance report for the period above using the
                specified sections and tone. Where specific metric values are "data pending",
                note that the data will be available upon full feature store integration and
                use qualitative language appropriate to the tone requested.
                """,
                distributor.getId(),
                distributor.getName(),
                period,
                reportDate,
                template.principal(),
                template.sections(),
                template.tone(),
                template.requiredMetrics()
        );

        return complianceReportAiService.generateReport(context);
    }

    private Counter successCounter() {
        return Counter.builder("zuqi_ai_compliance_reports_generated")
                .description("Total compliance reports successfully generated")
                .tag("job", "compliance_report")
                .register(meterRegistry);
    }

    private Counter failedCounter() {
        return Counter.builder("zuqi_ai_compliance_reports_failed")
                .description("Total compliance report generation failures")
                .tag("job", "compliance_report")
                .register(meterRegistry);
    }
}
