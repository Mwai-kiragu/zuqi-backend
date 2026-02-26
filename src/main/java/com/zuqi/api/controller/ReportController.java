package com.zuqi.api.controller;

import com.zuqi.api.dto.ApiResponse;
import com.zuqi.api.dto.report.InventoryReportResponse;
import com.zuqi.api.dto.report.PaymentReportResponse;
import com.zuqi.api.dto.report.SalesReportResponse;
import com.zuqi.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/v1/reports")
@RequiredArgsConstructor
@Tag(name = "Reports", description = "Report generation APIs")
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/sales")
    @Operation(summary = "Generate sales report", description = "Generates a sales report for the specified period")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DISTRIBUTOR_ADMIN', 'FINANCE')")
    public ResponseEntity<ApiResponse<SalesReportResponse>> getSalesReport(
            @Parameter(description = "Distributor ID") @RequestParam UUID distributorId,
            @Parameter(description = "Start date") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "End date") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        SalesReportResponse report = reportService.generateSalesReport(distributorId, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success(report));
    }

    @GetMapping("/inventory")
    @Operation(summary = "Generate inventory report", description = "Generates an inventory status report")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DISTRIBUTOR_ADMIN', 'WAREHOUSE_MANAGER')")
    public ResponseEntity<ApiResponse<InventoryReportResponse>> getInventoryReport(
            @Parameter(description = "Distributor ID") @RequestParam UUID distributorId) {

        InventoryReportResponse report = reportService.generateInventoryReport(distributorId);
        return ResponseEntity.ok(ApiResponse.success(report));
    }

    @GetMapping("/payments")
    @Operation(summary = "Generate payment report", description = "Generates a payment/collection report")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DISTRIBUTOR_ADMIN', 'FINANCE')")
    public ResponseEntity<ApiResponse<PaymentReportResponse>> getPaymentReport(
            @Parameter(description = "Distributor ID") @RequestParam UUID distributorId,
            @Parameter(description = "Start date") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "End date") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        PaymentReportResponse report = reportService.generatePaymentReport(distributorId, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success(report));
    }
}
