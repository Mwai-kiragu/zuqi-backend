package com.zuqi.api.controller;

import com.zuqi.api.dto.ApiResponse;
import com.zuqi.service.ExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/export")
@RequiredArgsConstructor
public class ExportController {

    private final ExportService exportService;

    @PostMapping("/customers/email")
    public ResponseEntity<ApiResponse<Void>> exportCustomersEmail() {
        exportService.exportCustomersToEmail();
        return ResponseEntity.ok(ApiResponse.success("Export is being prepared and will be sent to your email shortly."));
    }

    @PostMapping("/suppliers/email")
    public ResponseEntity<ApiResponse<Void>> exportSuppliersEmail() {
        exportService.exportSuppliersToEmail();
        return ResponseEntity.ok(ApiResponse.success("Export is being prepared and will be sent to your email shortly."));
    }

    @PostMapping("/products/email")
    public ResponseEntity<ApiResponse<Void>> exportProductsEmail() {
        exportService.exportProductsToEmail();
        return ResponseEntity.ok(ApiResponse.success("Export is being prepared and will be sent to your email shortly."));
    }

    @PostMapping("/inventory/email")
    public ResponseEntity<ApiResponse<Void>> exportInventoryEmail() {
        exportService.exportInventoryToEmail();
        return ResponseEntity.ok(ApiResponse.success("Export is being prepared and will be sent to your email shortly."));
    }

    @PostMapping("/invoices/email")
    public ResponseEntity<ApiResponse<Void>> exportInvoicesEmail() {
        exportService.exportInvoicesToEmail();
        return ResponseEntity.ok(ApiResponse.success("Export is being prepared and will be sent to your email shortly."));
    }

    @PostMapping("/warehouses/email")
    public ResponseEntity<ApiResponse<Void>> exportWarehousesEmail() {
        exportService.exportWarehousesToEmail();
        return ResponseEntity.ok(ApiResponse.success("Export is being prepared and will be sent to your email shortly."));
    }

    @PostMapping("/branches/email")
    public ResponseEntity<ApiResponse<Void>> exportBranchesEmail() {
        exportService.exportBranchesToEmail();
        return ResponseEntity.ok(ApiResponse.success("Export is being prepared and will be sent to your email shortly."));
    }

    @PostMapping("/categories/email")
    public ResponseEntity<ApiResponse<Void>> exportCategoriesEmail() {
        exportService.exportCategoriesToEmail();
        return ResponseEntity.ok(ApiResponse.success("Export is being prepared and will be sent to your email shortly."));
    }

    @PostMapping("/pos-sales/email")
    public ResponseEntity<ApiResponse<Void>> exportPosSalesEmail() {
        exportService.exportPosSalesToEmail();
        return ResponseEntity.ok(ApiResponse.success("Export is being prepared and will be sent to your email shortly."));
    }

    @PostMapping("/financial-report/email")
    public ResponseEntity<ApiResponse<Void>> exportFinancialReportEmail() {
        exportService.exportFinancialReportToEmail();
        return ResponseEntity.ok(ApiResponse.success("Export is being prepared and will be sent to your email shortly."));
    }

    @PostMapping("/price-lists/email")
    public ResponseEntity<ApiResponse<Void>> exportPriceListsEmail() {
        exportService.exportPriceListsToEmail();
        return ResponseEntity.ok(ApiResponse.success("Export is being prepared and will be sent to your email shortly."));
    }

    @PostMapping("/orders/email")
    public ResponseEntity<ApiResponse<Void>> exportOrdersEmail() {
        exportService.exportOrdersToEmail();
        return ResponseEntity.ok(ApiResponse.success("Export is being prepared and will be sent to your email shortly."));
    }

    @PostMapping("/payments/email")
    public ResponseEntity<ApiResponse<Void>> exportPaymentsEmail() {
        exportService.exportPaymentsToEmail();
        return ResponseEntity.ok(ApiResponse.success("Export is being prepared and will be sent to your email shortly."));
    }
}
