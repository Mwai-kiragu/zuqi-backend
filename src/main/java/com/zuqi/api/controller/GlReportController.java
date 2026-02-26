package com.zuqi.api.controller;

import com.zuqi.api.dto.ApiResponse;
import com.zuqi.api.dto.gl.BudgetVarianceResponse;
import com.zuqi.api.dto.gl.TrialBalanceResponse;
import com.zuqi.service.GlReportService;
import com.zuqi.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/gl/reports")
@RequiredArgsConstructor
@Tag(name = "GL Reports", description = "General Ledger reports")
public class GlReportController {

    private final GlReportService glReportService;
    private final SecurityUtils securityUtils;

    @GetMapping("/trial-balance")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DISTRIBUTOR_ADMIN','FINANCE')")
    @Operation(summary = "Get trial balance for a period")
    public ResponseEntity<ApiResponse<TrialBalanceResponse>> getTrialBalance(
            @RequestParam UUID periodId,
            @RequestParam(required = false) UUID distributorId) {
        UUID effectiveDistributorId = distributorId != null ? distributorId : securityUtils.getDistributorIdForFiltering();
        return ResponseEntity.ok(ApiResponse.success(glReportService.getTrialBalance(effectiveDistributorId, periodId)));
    }

    @GetMapping("/budget-variance")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DISTRIBUTOR_ADMIN','FINANCE')")
    @Operation(summary = "Get budget vs actual variance report")
    public ResponseEntity<ApiResponse<BudgetVarianceResponse>> getBudgetVariance(
            @RequestParam int year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) UUID distributorId) {
        UUID effectiveDistributorId = distributorId != null ? distributorId : securityUtils.getDistributorIdForFiltering();
        return ResponseEntity.ok(ApiResponse.success(glReportService.getBudgetVariance(effectiveDistributorId, year, month)));
    }
}
