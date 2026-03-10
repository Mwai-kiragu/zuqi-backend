package com.zuqi.api.controller;

import com.zuqi.api.dto.ApiResponse;
import com.zuqi.api.dto.accounting.BankReconciliationRequest;
import com.zuqi.api.dto.accounting.BankReconciliationResponse;
import com.zuqi.api.dto.common.PageResponse;
import com.zuqi.service.BankReconciliationService;
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

import java.util.UUID;

@RestController
@RequestMapping("/v1/accounting/bank-reconciliations")
@RequiredArgsConstructor
@Tag(name = "Bank Reconciliation", description = "Bank reconciliation management")
public class BankReconciliationController {

    private final BankReconciliationService bankReconciliationService;
    private final SecurityUtils securityUtils;

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DISTRIBUTOR_ADMIN','FINANCE')")
    @Operation(summary = "Create a bank reconciliation")
    public ResponseEntity<ApiResponse<BankReconciliationResponse>> create(@Valid @RequestBody BankReconciliationRequest request) {
        UUID distributorId = securityUtils.getDistributorIdForFiltering();
        return ResponseEntity.ok(ApiResponse.success(bankReconciliationService.create(distributorId, request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DISTRIBUTOR_ADMIN','FINANCE')")
    @Operation(summary = "Update a bank reconciliation")
    public ResponseEntity<ApiResponse<BankReconciliationResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody BankReconciliationRequest request) {
        return ResponseEntity.ok(ApiResponse.success(bankReconciliationService.update(id, request)));
    }

    @PostMapping("/{id}/reconcile")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DISTRIBUTOR_ADMIN','FINANCE')")
    @Operation(summary = "Mark a bank reconciliation as reconciled")
    public ResponseEntity<ApiResponse<BankReconciliationResponse>> reconcile(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(bankReconciliationService.reconcile(id)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DISTRIBUTOR_ADMIN','FINANCE')")
    @Operation(summary = "Get a bank reconciliation by ID")
    public ResponseEntity<ApiResponse<BankReconciliationResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(bankReconciliationService.getById(id)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DISTRIBUTOR_ADMIN','FINANCE')")
    @Operation(summary = "List bank reconciliations")
    public ResponseEntity<ApiResponse<Page<BankReconciliationResponse>>> getAll(Pageable pageable) {
        UUID distributorId = securityUtils.getDistributorIdForFiltering();
        return ResponseEntity.ok(ApiResponse.success(bankReconciliationService.getAll(distributorId, pageable)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DISTRIBUTOR_ADMIN','FINANCE')")
    @Operation(summary = "Delete a bank reconciliation")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        bankReconciliationService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
