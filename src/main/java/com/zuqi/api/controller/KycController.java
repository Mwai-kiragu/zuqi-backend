package com.zuqi.api.controller;

import com.zuqi.api.dto.ApiResponse;
import com.zuqi.api.dto.kyc.DistributorKycRequest;
import com.zuqi.api.dto.kyc.KycApplicationResponse;
import com.zuqi.api.dto.kyc.KycRejectRequest;
import com.zuqi.api.dto.kyc.MerchantKycRequest;
import com.zuqi.domain.user.User;
import com.zuqi.service.KycService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/kyc")
@RequiredArgsConstructor
@Tag(name = "KYC", description = "KYC onboarding endpoints")
public class KycController {

    private final KycService kycService;

    @PostMapping("/merchant")
    @Operation(summary = "Submit merchant KYC", description = "Submit KYC information for merchant onboarding")
    public ResponseEntity<ApiResponse<Void>> submitMerchantKyc(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody MerchantKycRequest request) {
        kycService.submitMerchantKyc(user.getId(), request);
        return ResponseEntity.ok(ApiResponse.success("Merchant KYC submitted successfully"));
    }

    @PostMapping("/distributor")
    @Operation(summary = "Submit distributor KYC", description = "Submit KYC information for distributor onboarding")
    public ResponseEntity<ApiResponse<Void>> submitDistributorKyc(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody DistributorKycRequest request) {
        kycService.submitDistributorKyc(user.getId(), request);
        return ResponseEntity.ok(ApiResponse.success("Distributor KYC submitted successfully"));
    }

    @GetMapping("/status")
    @Operation(summary = "Get KYC status", description = "Get the current user's KYC status")
    public ResponseEntity<ApiResponse<String>> getKycStatus(
            @AuthenticationPrincipal User user) {
        String status = kycService.getKycStatus(user.getId());
        return ResponseEntity.ok(ApiResponse.success("KYC status retrieved", status));
    }

    @GetMapping("/applications")
    @Operation(summary = "List KYC applications", description = "List KYC applications with optional status filter (Admin)")
    public ResponseEntity<ApiResponse<Page<KycApplicationResponse>>> getApplications(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<KycApplicationResponse> applications = kycService.getApplications(
                status, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return ResponseEntity.ok(ApiResponse.success("KYC applications retrieved", applications));
    }

    @PostMapping("/applications/{id}/approve")
    @Operation(summary = "Approve KYC application", description = "Approve a merchant or distributor KYC application (Admin)")
    public ResponseEntity<ApiResponse<Void>> approveKyc(
            @PathVariable UUID id,
            @RequestParam String type) {
        kycService.approveKyc(id, type);
        return ResponseEntity.ok(ApiResponse.success("KYC application approved successfully"));
    }

    @PostMapping("/applications/{id}/reject")
    @Operation(summary = "Reject KYC application", description = "Reject a merchant or distributor KYC application (Admin)")
    public ResponseEntity<ApiResponse<Void>> rejectKyc(
            @PathVariable UUID id,
            @RequestParam String type,
            @Valid @RequestBody KycRejectRequest request) {
        kycService.rejectKyc(id, type, request.getReason());
        return ResponseEntity.ok(ApiResponse.success("KYC application rejected"));
    }
}
