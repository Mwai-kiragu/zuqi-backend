package com.zuqi.api.controller;

import com.zuqi.api.dto.ApiResponse;
import com.zuqi.api.dto.gl.*;
import com.zuqi.service.GlReportService;
import com.zuqi.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
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

    @GetMapping("/general-ledger")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DISTRIBUTOR_ADMIN','FINANCE')")
    @Operation(summary = "Get general ledger report for a date range")
    public ResponseEntity<ApiResponse<GeneralLedgerResponse>> getGeneralLedger(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) UUID distributorId) {
        UUID effectiveDistributorId = distributorId != null ? distributorId : securityUtils.getDistributorIdForFiltering();
        return ResponseEntity.ok(ApiResponse.success(glReportService.getGeneralLedger(effectiveDistributorId, fromDate, toDate)));
    }

    @GetMapping("/balance-sheet")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DISTRIBUTOR_ADMIN','FINANCE')")
    @Operation(summary = "Get balance sheet as of a date")
    public ResponseEntity<ApiResponse<BalanceSheetResponse>> getBalanceSheet(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate,
            @RequestParam(required = false) UUID distributorId) {
        UUID effectiveDistributorId = distributorId != null ? distributorId : securityUtils.getDistributorIdForFiltering();
        return ResponseEntity.ok(ApiResponse.success(glReportService.getBalanceSheet(effectiveDistributorId, asOfDate)));
    }

    @GetMapping("/profit-loss")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DISTRIBUTOR_ADMIN','FINANCE')")
    @Operation(summary = "Get profit & loss statement for a date range")
    public ResponseEntity<ApiResponse<ProfitLossResponse>> getProfitAndLoss(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) UUID distributorId) {
        UUID effectiveDistributorId = distributorId != null ? distributorId : securityUtils.getDistributorIdForFiltering();
        return ResponseEntity.ok(ApiResponse.success(glReportService.getProfitAndLoss(effectiveDistributorId, fromDate, toDate)));
    }

    @GetMapping("/cash-flow")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DISTRIBUTOR_ADMIN','FINANCE')")
    @Operation(summary = "Get cash flow statement (indirect method) for a date range")
    public ResponseEntity<ApiResponse<CashFlowResponse>> getCashFlowStatement(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) UUID distributorId) {
        UUID effectiveDistributorId = distributorId != null ? distributorId : securityUtils.getDistributorIdForFiltering();
        return ResponseEntity.ok(ApiResponse.success(glReportService.getCashFlowStatement(effectiveDistributorId, fromDate, toDate)));
    }
}
