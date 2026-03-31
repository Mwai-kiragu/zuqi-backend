package com.zuqi.api.controller;

import com.zuqi.api.dto.ApiResponse;
import com.zuqi.api.dto.common.DeactivateRequest;
import com.zuqi.api.dto.supplier.SupplierCategoryRequest;
import com.zuqi.api.dto.supplier.SupplierCategoryResponse;
import com.zuqi.api.dto.supplier.SupplierRequest;
import com.zuqi.api.dto.supplier.SupplierResponse;
import com.zuqi.domain.user.User;
import com.zuqi.service.SupplierService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/suppliers")
@RequiredArgsConstructor
@Tag(name = "Suppliers", description = "Supplier/Vendor management APIs")
public class SupplierController {

    private final SupplierService supplierService;

    @GetMapping
    @Operation(summary = "Get all suppliers", description = "Retrieves suppliers with pagination and optional search")
    public ResponseEntity<ApiResponse<Page<SupplierResponse>>> getAllSuppliers(
            @Parameter(description = "Search term") @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(supplierService.getAllSuppliers(search, pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get supplier by ID")
    public ResponseEntity<ApiResponse<SupplierResponse>> getSupplierById(
            @Parameter(description = "Supplier ID") @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(supplierService.getSupplierById(id)));
    }

    @PostMapping
    @Operation(summary = "Create supplier", description = "Creates a new supplier/vendor")
    public ResponseEntity<ApiResponse<SupplierResponse>> createSupplier(
            @Valid @RequestBody SupplierRequest request) {
        SupplierResponse supplier = supplierService.createSupplier(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Supplier created successfully", supplier));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update supplier", description = "Updates an existing supplier")
    public ResponseEntity<ApiResponse<SupplierResponse>> updateSupplier(
            @Parameter(description = "Supplier ID") @PathVariable UUID id,
            @Valid @RequestBody SupplierRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Supplier updated successfully", supplierService.updateSupplier(id, request)));
    }

    @PatchMapping("/{id}/verify")
    @Operation(summary = "Verify supplier", description = "Marks a supplier as KYC-verified")
    public ResponseEntity<ApiResponse<SupplierResponse>> verifySupplier(
            @Parameter(description = "Supplier ID") @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Supplier verified successfully", supplierService.verifySupplier(id)));
    }

    @PostMapping("/{id}/blacklist")
    @Operation(summary = "Blacklist supplier", description = "Blacklists a supplier with a reason")
    public ResponseEntity<ApiResponse<SupplierResponse>> blacklistSupplier(
            @Parameter(description = "Supplier ID") @PathVariable UUID id,
            @RequestBody @Valid BlacklistBody body,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success("Supplier blacklisted successfully",
                supplierService.blacklistSupplier(id, body.reason(), currentUser)));
    }

    @PostMapping("/{id}/unblacklist")
    @Operation(summary = "Remove supplier from blacklist")
    public ResponseEntity<ApiResponse<SupplierResponse>> unblacklistSupplier(
            @Parameter(description = "Supplier ID") @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Supplier removed from blacklist", supplierService.unblacklistSupplier(id)));
    }

    @GetMapping("/blacklisted")
    @Operation(summary = "Get blacklisted suppliers")
    public ResponseEntity<ApiResponse<Page<SupplierResponse>>> getBlacklistedSuppliers(
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(supplierService.getBlacklistedSuppliers(pageable)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Deactivate supplier", description = "Deactivates a supplier (soft delete)")
    public ResponseEntity<ApiResponse<Void>> deactivateSupplier(
            @Parameter(description = "Supplier ID") @PathVariable UUID id,
            @Valid @RequestBody DeactivateRequest request,
            @AuthenticationPrincipal User currentUser) {
        supplierService.deactivateSupplier(id, request.getReason(), currentUser);
        return ResponseEntity.ok(ApiResponse.success("Supplier deactivated successfully"));
    }

    @PostMapping("/{id}/activate")
    @Operation(summary = "Activate supplier", description = "Reactivates a deactivated supplier")
    public ResponseEntity<ApiResponse<Void>> activateSupplier(
            @Parameter(description = "Supplier ID") @PathVariable UUID id) {
        supplierService.activateSupplier(id);
        return ResponseEntity.ok(ApiResponse.success("Supplier activated successfully"));
    }

    // --- Categories ---

    @GetMapping("/categories")
    @Operation(summary = "Get all supplier categories")
    public ResponseEntity<ApiResponse<List<SupplierCategoryResponse>>> getCategories() {
        return ResponseEntity.ok(ApiResponse.success(supplierService.getAllCategories()));
    }

    @PostMapping("/categories")
    @Operation(summary = "Create supplier category")
    public ResponseEntity<ApiResponse<SupplierCategoryResponse>> createCategory(
            @Valid @RequestBody SupplierCategoryRequest request) {
        SupplierCategoryResponse category = supplierService.createCategory(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Category created successfully", category));
    }

    @PutMapping("/categories/{id}")
    @Operation(summary = "Update supplier category")
    public ResponseEntity<ApiResponse<SupplierCategoryResponse>> updateCategory(
            @Parameter(description = "Category ID") @PathVariable Long id,
            @Valid @RequestBody SupplierCategoryRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Category updated successfully", supplierService.updateCategory(id, request)));
    }

    @DeleteMapping("/categories/{id}")
    @Operation(summary = "Delete supplier category")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(
            @Parameter(description = "Category ID") @PathVariable Long id) {
        supplierService.deleteCategory(id);
        return ResponseEntity.ok(ApiResponse.success("Category deleted successfully"));
    }

    // Inner record for blacklist request body
    record BlacklistBody(String reason) {}
}
