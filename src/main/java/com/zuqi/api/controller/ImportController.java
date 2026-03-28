package com.zuqi.api.controller;

import com.zuqi.api.dto.ApiResponse;
import com.zuqi.service.ImportService;
import com.zuqi.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/v1/import")
@RequiredArgsConstructor
@Tag(name = "Bulk Import", description = "CSV bulk import endpoints")
public class ImportController {

    private final ImportService importService;
    private final SecurityUtils securityUtils;

    @PostMapping(value = "/customers", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Bulk import customers from CSV")
    public ResponseEntity<ApiResponse<ImportService.ImportResult>> importCustomers(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "distributorId", required = false) UUID distributorId) {

        UUID resolvedDistributorId = resolveDistributorId(distributorId);
        ImportService.ImportResult result = importService.importCustomers(file, resolvedDistributorId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping(value = "/suppliers", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Bulk import suppliers from CSV")
    public ResponseEntity<ApiResponse<ImportService.ImportResult>> importSuppliers(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "distributorId", required = false) UUID distributorId) {

        UUID resolvedDistributorId = resolveDistributorId(distributorId);
        ImportService.ImportResult result = importService.importSuppliers(file, resolvedDistributorId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping(value = "/products", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Bulk import products from CSV")
    public ResponseEntity<ApiResponse<ImportService.ImportResult>> importProducts(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "distributorId", required = false) UUID distributorId) {

        UUID resolvedDistributorId = resolveDistributorId(distributorId);
        if (resolvedDistributorId == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("distributorId is required for product import"));
        }
        ImportService.ImportResult result = importService.importProducts(file, resolvedDistributorId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping(value = "/categories", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Bulk import product categories from CSV")
    public ResponseEntity<ApiResponse<ImportService.ImportResult>> importCategories(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "distributorId", required = false) UUID distributorId) {

        UUID resolvedDistributorId = resolveDistributorId(distributorId);
        if (resolvedDistributorId == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("distributorId is required for category import"));
        }
        ImportService.ImportResult result = importService.importCategories(file, resolvedDistributorId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    private UUID resolveDistributorId(UUID requested) {
        if (requested != null) return requested;
        return securityUtils.getDistributorIdForFiltering();
    }
}
