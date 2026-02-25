package com.zuqi.api.controller;

import com.zuqi.ai.reporting.ComplianceReportJob;
import com.zuqi.ai.reporting.ReportTemplateRegistry;
import com.zuqi.api.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST API for LLM-powered compliance report generation.
 *
 * Authorization is handled by Casbin policy.csv — no {@code @PreAuthorize} needed.
 *
 * Policy entries (from policy.csv):
 * <pre>
 *   p, DISTRIBUTOR_ADMIN, /v1/ai/reports/compliance/generate, POST
 *   p, DISTRIBUTOR_ADMIN, /v1/ai/reports/compliance/:id, GET
 * </pre>
 *
 * Blueprint reference: implementation_plan.md Phase 6 Task 6.3
 */
@RestController
@RequestMapping("/v1/ai/reports")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "AI - Compliance Reports", description = "LLM-powered compliance report generation")
public class AiReportController {

    private final ComplianceReportJob      complianceReportJob;
    private final ReportTemplateRegistry   reportTemplateRegistry;

    // ── POST /compliance/generate ──────────────────────────────────────────

    /**
     * Trigger on-demand compliance report generation for a distributor.
     *
     * If a {@code principal} key is supplied (e.g. "UNILEVER", "P_AND_G", "EABL"),
     * the principal's specific template is used. If omitted the DEFAULT template applies.
     *
     * @param distributorId UUID of the target distributor
     * @param principal     Optional principal key (case-insensitive)
     * @return Generated markdown compliance report
     */
    @PostMapping("/compliance/generate")
    @Operation(
            summary = "Generate a compliance report for a distributor",
            description = "Triggers immediate LLM-powered compliance report generation. " +
                          "Supply a principal key (UNILEVER, P_AND_G, EABL) to use a " +
                          "principal-specific template; omit for the DEFAULT template.")
    public ResponseEntity<ApiResponse<String>> generateComplianceReport(
            @Parameter(required = true, description = "UUID of the distributor")
            @RequestParam UUID distributorId,
            @Parameter(description = "Principal key: UNILEVER | P_AND_G | EABL (optional)")
            @RequestParam(required = false) String principal) {

        log.info("POST /v1/ai/reports/compliance/generate distributor={} principal={}",
                distributorId, principal);

        try {
            String report = (principal != null && !principal.isBlank())
                    ? complianceReportJob.generateForDistributorAndPrincipal(distributorId, principal)
                    : complianceReportJob.generateForDistributor(distributorId);

            return ResponseEntity.ok(
                    ApiResponse.success("Compliance report generated successfully", report));

        } catch (IllegalArgumentException e) {
            log.warn("Compliance report request rejected: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Compliance report generation failed for distributor={}: {}",
                    distributorId, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Report generation failed: " + e.getMessage()));
        }
    }

    // ── GET /compliance/templates ──────────────────────────────────────────

    /**
     * Return the list of supported principal keys that have dedicated report templates.
     *
     * Useful for populating a dropdown in the frontend before the caller
     * submits a {@code /compliance/generate} request.
     *
     * @return List of supported principal keys (e.g. ["EABL", "P_AND_G", "UNILEVER"])
     */
    @GetMapping("/compliance/templates")
    @Operation(
            summary = "List supported compliance report templates",
            description = "Returns the set of principal keys that have dedicated report templates. " +
                          "Pass one of these as the 'principal' parameter to /compliance/generate.")
    public ResponseEntity<ApiResponse<List<String>>> getSupportedTemplates() {

        log.info("GET /v1/ai/reports/compliance/templates");

        List<String> principals = reportTemplateRegistry.getSupportedPrincipals();
        return ResponseEntity.ok(ApiResponse.success(principals));
    }
}
