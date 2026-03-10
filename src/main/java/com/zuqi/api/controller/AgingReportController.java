package com.zuqi.api.controller;

import com.zuqi.api.dto.ApiResponse;
import com.zuqi.api.dto.aging.ApAgingResponse;
import com.zuqi.api.dto.aging.ArAgingResponse;
import com.zuqi.service.AgingReportService;
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
@RequestMapping("/v1/reports/aging")
@RequiredArgsConstructor
@Tag(name = "Aging Reports", description = "AR and AP aging reports")
public class AgingReportController {

    private final AgingReportService agingReportService;
    private final SecurityUtils securityUtils;

    @GetMapping("/ar")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DISTRIBUTOR_ADMIN','FINANCE')")
    @Operation(summary = "Get Accounts Receivable aging report")
    public ResponseEntity<ApiResponse<ArAgingResponse>> getArAging(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate,
            @RequestParam(required = false) UUID distributorId) {
        if (asOfDate == null) asOfDate = LocalDate.now();
        UUID effectiveDistributorId = distributorId != null ? distributorId : securityUtils.getDistributorIdForFiltering();
        return ResponseEntity.ok(ApiResponse.success(agingReportService.getArAging(effectiveDistributorId, asOfDate)));
    }

    @GetMapping("/ap")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DISTRIBUTOR_ADMIN','FINANCE')")
    @Operation(summary = "Get Accounts Payable aging report")
    public ResponseEntity<ApiResponse<ApAgingResponse>> getApAging(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate,
            @RequestParam(required = false) UUID distributorId) {
        if (asOfDate == null) asOfDate = LocalDate.now();
        UUID effectiveDistributorId = distributorId != null ? distributorId : securityUtils.getDistributorIdForFiltering();
        return ResponseEntity.ok(ApiResponse.success(agingReportService.getApAging(effectiveDistributorId, asOfDate)));
    }
}
