package com.zuqi.api.controller;

import com.zuqi.api.dto.ApiResponse;
import com.zuqi.api.dto.common.DeactivateRequest;
import com.zuqi.api.dto.merchant.MerchantCategoryResponse;
import com.zuqi.api.dto.merchant.MerchantRequest;
import com.zuqi.api.dto.merchant.MerchantResponse;
import com.zuqi.domain.user.User;
import com.zuqi.service.MerchantService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for merchant operations.
 */
@RestController
@RequestMapping("/v1/merchants")
@RequiredArgsConstructor
@Tag(name = "Merchants", description = "Merchant management APIs")
public class MerchantController {

    private final MerchantService merchantService;

    /**
     * Get all merchants with pagination and optional filters.
     */
    @GetMapping
    @Operation(summary = "Get all merchants", description = "Retrieves merchants with pagination and optional filters")
    public ResponseEntity<ApiResponse<Page<MerchantResponse>>> getAllMerchants(
            @Parameter(description = "Distributor ID filter") @RequestParam(required = false) UUID distributorId,
            @Parameter(description = "Category ID filter") @RequestParam(required = false) Long categoryId,
            @Parameter(description = "Sales rep ID filter") @RequestParam(required = false) UUID salesRepId,
            @Parameter(description = "Search term") @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "businessName", direction = Sort.Direction.ASC) Pageable pageable) {

        Page<MerchantResponse> merchants;

        if (search != null && !search.isBlank()) {
            merchants = merchantService.searchMerchants(search, distributorId, pageable);
        } else if (distributorId != null) {
            merchants = merchantService.getMerchantsByDistributor(distributorId, pageable);
        } else if (categoryId != null) {
            merchants = merchantService.getMerchantsByCategory(categoryId, pageable);
        } else if (salesRepId != null) {
            merchants = merchantService.getMerchantsBySalesRep(salesRepId, pageable);
        } else {
            merchants = merchantService.getAllMerchants(pageable);
        }

        return ResponseEntity.ok(ApiResponse.success(merchants));
    }

    /**
     * Get a merchant by ID.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get merchant by ID", description = "Retrieves a specific merchant by ID")
    public ResponseEntity<ApiResponse<MerchantResponse>> getMerchantById(
            @Parameter(description = "Merchant ID") @PathVariable UUID id) {
        MerchantResponse merchant = merchantService.getMerchantById(id);
        return ResponseEntity.ok(ApiResponse.success(merchant));
    }

    /**
     * Create a new merchant.
     */
    @PostMapping
    @Operation(summary = "Create merchant", description = "Creates a new merchant")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'DISTRIBUTOR_ADMIN', 'SALES_REP')")
    public ResponseEntity<ApiResponse<MerchantResponse>> createMerchant(
            @Valid @RequestBody MerchantRequest request) {
        MerchantResponse merchant = merchantService.createMerchant(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Merchant created successfully", merchant));
    }

    /**
     * Update an existing merchant.
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update merchant", description = "Updates an existing merchant")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'DISTRIBUTOR_ADMIN', 'SALES_REP')")
    public ResponseEntity<ApiResponse<MerchantResponse>> updateMerchant(
            @Parameter(description = "Merchant ID") @PathVariable UUID id,
            @Valid @RequestBody MerchantRequest request) {
        MerchantResponse merchant = merchantService.updateMerchant(id, request);
        return ResponseEntity.ok(ApiResponse.success("Merchant updated successfully", merchant));
    }

    /**
     * Assign a sales rep to a merchant.
     */
    @PatchMapping("/{id}/assign")
    @Operation(summary = "Assign sales rep", description = "Assigns a sales rep to a merchant")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'DISTRIBUTOR_ADMIN')")
    public ResponseEntity<ApiResponse<MerchantResponse>> assignSalesRep(
            @Parameter(description = "Merchant ID") @PathVariable UUID id,
            @Parameter(description = "Sales Rep ID") @RequestParam UUID salesRepId) {
        MerchantResponse merchant = merchantService.assignSalesRep(id, salesRepId);
        return ResponseEntity.ok(ApiResponse.success("Sales rep assigned successfully", merchant));
    }

    /**
     * Verify a merchant.
     */
    @PatchMapping("/{id}/verify")
    @Operation(summary = "Verify merchant", description = "Marks a merchant as verified")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'DISTRIBUTOR_ADMIN')")
    public ResponseEntity<ApiResponse<MerchantResponse>> verifyMerchant(
            @Parameter(description = "Merchant ID") @PathVariable UUID id) {
        MerchantResponse merchant = merchantService.verifyMerchant(id);
        return ResponseEntity.ok(ApiResponse.success("Merchant verified successfully", merchant));
    }

    /**
     * Deactivate a merchant with reason.
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Deactivate merchant", description = "Deactivates a merchant (soft delete) with reason")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'DISTRIBUTOR_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deactivateMerchant(
            @Parameter(description = "Merchant ID") @PathVariable UUID id,
            @Valid @RequestBody DeactivateRequest request,
            @AuthenticationPrincipal User currentUser) {
        merchantService.deactivateMerchant(id, request.getReason(), currentUser);
        return ResponseEntity.ok(ApiResponse.success("Merchant deactivated successfully"));
    }

    /**
     * Activate a merchant.
     */
    @PostMapping("/{id}/activate")
    @Operation(summary = "Activate merchant", description = "Reactivates a deactivated merchant")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'DISTRIBUTOR_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> activateMerchant(
            @Parameter(description = "Merchant ID") @PathVariable UUID id) {
        merchantService.activateMerchant(id);
        return ResponseEntity.ok(ApiResponse.success("Merchant activated successfully"));
    }

    /**
     * Get all merchant categories.
     */
    @GetMapping("/categories")
    @Operation(summary = "Get merchant categories", description = "Retrieves all merchant categories")
    public ResponseEntity<ApiResponse<List<MerchantCategoryResponse>>> getCategories() {
        List<MerchantCategoryResponse> categories = merchantService.getAllCategories();
        return ResponseEntity.ok(ApiResponse.success(categories));
    }

    /**
     * Get distinct cities for filtering.
     */
    @GetMapping("/cities")
    @Operation(summary = "Get distinct cities", description = "Retrieves distinct cities for filtering")
    public ResponseEntity<ApiResponse<List<String>>> getCities() {
        List<String> cities = merchantService.getDistinctCities();
        return ResponseEntity.ok(ApiResponse.success(cities));
    }
}
