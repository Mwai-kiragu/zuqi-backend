package com.zuqi.api.controller;

import com.zuqi.api.dto.ApiResponse;
import com.zuqi.api.dto.accounting.TaxRateRequest;
import com.zuqi.api.dto.accounting.TaxRateResponse;
import com.zuqi.service.TaxRateService;
import com.zuqi.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/accounting/tax-rates")
@RequiredArgsConstructor
@Tag(name = "Tax Rates", description = "Tax rate management")
public class TaxRateController {

    private final TaxRateService taxRateService;
    private final SecurityUtils securityUtils;

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MERCHANT_ADMIN','DISTRIBUTOR_ADMIN','FINANCE')")
    @Operation(summary = "Create a tax rate")
    public ResponseEntity<ApiResponse<TaxRateResponse>> create(@Valid @RequestBody TaxRateRequest request) {
        UUID distributorId = securityUtils.getDistributorIdForFiltering();
        return ResponseEntity.ok(ApiResponse.success(taxRateService.create(distributorId, request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MERCHANT_ADMIN','DISTRIBUTOR_ADMIN','FINANCE')")
    @Operation(summary = "Update a tax rate")
    public ResponseEntity<ApiResponse<TaxRateResponse>> update(
            @PathVariable UUID id, @Valid @RequestBody TaxRateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(taxRateService.update(id, request)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a tax rate by ID")
    public ResponseEntity<ApiResponse<TaxRateResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(taxRateService.getById(id)));
    }

    @GetMapping
    @Operation(summary = "List all tax rates")
    public ResponseEntity<ApiResponse<Page<TaxRateResponse>>> getAll(Pageable pageable) {
        UUID distributorId = securityUtils.getDistributorIdForFiltering();
        return ResponseEntity.ok(ApiResponse.success(taxRateService.getAll(distributorId, pageable)));
    }

    @GetMapping("/active")
    @Operation(summary = "Get active tax rates (for dropdowns)")
    public ResponseEntity<ApiResponse<List<TaxRateResponse>>> getActive() {
        UUID distributorId = securityUtils.getDistributorIdForFiltering();
        return ResponseEntity.ok(ApiResponse.success(taxRateService.getActive(distributorId)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MERCHANT_ADMIN','DISTRIBUTOR_ADMIN')")
    @Operation(summary = "Delete a tax rate")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        taxRateService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
