package com.zuqi.api.controller;

import com.zuqi.api.dto.ApiResponse;
import com.zuqi.api.dto.report.InventoryReportResponse;
import com.zuqi.api.dto.report.PaymentReportResponse;
import com.zuqi.api.dto.report.SalesReportResponse;
import com.zuqi.exception.ValidationException;
import com.zuqi.service.ReportService;
import com.zuqi.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/v1/reports")
@RequiredArgsConstructor
@Tag(name = "Reports", description = "Report generation APIs")
public class ReportController {

    private final ReportService reportService;
    private final SecurityUtils securityUtils;

    @GetMapping("/sales")
    @Operation(summary = "Generate sales report")
    public ResponseEntity<ApiResponse<SalesReportResponse>> getSalesReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        return ResponseEntity.ok(ApiResponse.success(reportService.generateSalesReport(startDate, endDate)));
    }

    @GetMapping("/inventory")
    @Operation(summary = "Generate inventory / stock valuation report")
    public ResponseEntity<ApiResponse<InventoryReportResponse>> getInventoryReport() {
        return ResponseEntity.ok(ApiResponse.success(reportService.generateInventoryReport()));
    }

    @GetMapping("/payments")
    @Operation(summary = "Generate payment report")
    public ResponseEntity<ApiResponse<PaymentReportResponse>> getPaymentReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        return ResponseEntity.ok(ApiResponse.success(reportService.generatePaymentReport(startDate, endDate)));
    }
}
