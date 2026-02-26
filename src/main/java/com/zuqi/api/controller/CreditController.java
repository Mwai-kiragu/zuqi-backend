package com.zuqi.api.controller;

import com.zuqi.api.dto.ApiResponse;
import com.zuqi.api.dto.credit.CreditLimitRequest;
import com.zuqi.api.dto.credit.CreditLimitResponse;
import com.zuqi.api.dto.credit.CreditScoreResponse;
import com.zuqi.domain.credit.CreditLimitStatus;
import com.zuqi.service.CreditService;
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

import java.util.UUID;

@RestController
@RequestMapping("/v1/credit")
@RequiredArgsConstructor
@Tag(name = "Credit", description = "Credit management APIs")
public class CreditController {

    private final CreditService creditService;

    @GetMapping("/limits")
    @Operation(summary = "Get credit limits", description = "Retrieves credit limits with pagination and filters")
    public ResponseEntity<ApiResponse<Page<CreditLimitResponse>>> getCreditLimits(
            @Parameter(description = "Distributor ID") @RequestParam UUID distributorId,
            @Parameter(description = "Merchant ID filter") @RequestParam(required = false) UUID merchantId,
            @Parameter(description = "Status filter") @RequestParam(required = false) CreditLimitStatus status,
            @Parameter(description = "Search term") @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<CreditLimitResponse> limits;
        if (search != null && !search.isBlank()) {
            limits = creditService.searchCreditLimits(distributorId, search, pageable);
        } else {
            limits = creditService.getCreditLimits(distributorId, merchantId, status, pageable);
        }

        return ResponseEntity.ok(ApiResponse.success(limits));
    }

    @GetMapping("/limits/{id}")
    @Operation(summary = "Get credit limit by ID", description = "Retrieves a specific credit limit")
    public ResponseEntity<ApiResponse<CreditLimitResponse>> getCreditLimitById(
            @Parameter(description = "Credit limit ID") @PathVariable UUID id) {

        CreditLimitResponse limit = creditService.getCreditLimitById(id);
        return ResponseEntity.ok(ApiResponse.success(limit));
    }

    @GetMapping("/limits/merchant/{merchantId}")
    @Operation(summary = "Get active credit limit", description = "Retrieves the active credit limit for a merchant")
    public ResponseEntity<ApiResponse<CreditLimitResponse>> getActiveCreditLimit(
            @Parameter(description = "Merchant ID") @PathVariable UUID merchantId,
            @Parameter(description = "Distributor ID") @RequestParam UUID distributorId) {

        CreditLimitResponse limit = creditService.getActiveCreditLimit(merchantId, distributorId);
        return ResponseEntity.ok(ApiResponse.success(limit));
    }

    @PostMapping("/limits")
    @Operation(summary = "Create credit limit", description = "Creates a new credit limit for a merchant")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DISTRIBUTOR_ADMIN')")
    public ResponseEntity<ApiResponse<CreditLimitResponse>> createCreditLimit(
            @Valid @RequestBody CreditLimitRequest request,
            @Parameter(description = "Approving user ID") @RequestParam UUID approvedById) {

        CreditLimitResponse limit = creditService.createCreditLimit(request, approvedById);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Credit limit created successfully", limit));
    }

    @PutMapping("/limits/{id}")
    @Operation(summary = "Update credit limit", description = "Updates an existing credit limit")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DISTRIBUTOR_ADMIN')")
    public ResponseEntity<ApiResponse<CreditLimitResponse>> updateCreditLimit(
            @Parameter(description = "Credit limit ID") @PathVariable UUID id,
            @Valid @RequestBody CreditLimitRequest request) {

        CreditLimitResponse limit = creditService.updateCreditLimit(id, request);
        return ResponseEntity.ok(ApiResponse.success("Credit limit updated successfully", limit));
    }

    @PatchMapping("/limits/{id}/suspend")
    @Operation(summary = "Suspend credit limit", description = "Suspends a credit limit")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DISTRIBUTOR_ADMIN')")
    public ResponseEntity<ApiResponse<CreditLimitResponse>> suspendCreditLimit(
            @Parameter(description = "Credit limit ID") @PathVariable UUID id) {

        CreditLimitResponse limit = creditService.suspendCreditLimit(id);
        return ResponseEntity.ok(ApiResponse.success("Credit limit suspended successfully", limit));
    }

    @PatchMapping("/limits/{id}/reactivate")
    @Operation(summary = "Reactivate credit limit", description = "Reactivates a suspended credit limit")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DISTRIBUTOR_ADMIN')")
    public ResponseEntity<ApiResponse<CreditLimitResponse>> reactivateCreditLimit(
            @Parameter(description = "Credit limit ID") @PathVariable UUID id) {

        CreditLimitResponse limit = creditService.reactivateCreditLimit(id);
        return ResponseEntity.ok(ApiResponse.success("Credit limit reactivated successfully", limit));
    }

    @GetMapping("/scores/merchant/{merchantId}")
    @Operation(summary = "Get merchant credit score", description = "Retrieves the latest credit score for a merchant")
    public ResponseEntity<ApiResponse<CreditScoreResponse>> getMerchantCreditScore(
            @Parameter(description = "Merchant ID") @PathVariable UUID merchantId) {

        CreditScoreResponse score = creditService.getMerchantCreditScore(merchantId);
        return ResponseEntity.ok(ApiResponse.success(score));
    }

    @GetMapping("/scores/merchant/{merchantId}/history")
    @Operation(summary = "Get credit score history", description = "Retrieves credit score history for a merchant")
    public ResponseEntity<ApiResponse<Page<CreditScoreResponse>>> getCreditScoreHistory(
            @Parameter(description = "Merchant ID") @PathVariable UUID merchantId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<CreditScoreResponse> scores = creditService.getCreditScoreHistory(merchantId, pageable);
        return ResponseEntity.ok(ApiResponse.success(scores));
    }
}
