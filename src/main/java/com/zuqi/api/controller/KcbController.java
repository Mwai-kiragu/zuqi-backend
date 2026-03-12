package com.zuqi.api.controller;

import com.zuqi.api.dto.ApiResponse;
import com.zuqi.api.dto.kcb.KcbActivateRequest;
import com.zuqi.api.dto.kcb.KcbConfigResponse;
import com.zuqi.api.dto.kcb.KcbStkPushRequest;
import com.zuqi.api.dto.kcb.KcbStkPushResponse;
import com.zuqi.exception.ValidationException;
import com.zuqi.service.KcbService;
import com.zuqi.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/kcb")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "KCB", description = "KCB payment configuration and STK push APIs")
public class KcbController {

    private final KcbService kcbService;
    private final SecurityUtils securityUtils;

    @PostMapping("/activate")
    @Operation(summary = "Activate KCB config for the current merchant")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MERCHANT_ADMIN')")
    public ResponseEntity<ApiResponse<KcbConfigResponse>> activateConfig(
            @Valid @RequestBody KcbActivateRequest request) {

        UUID merchantId = resolveMerchantId(null);
        KcbConfigResponse response = kcbService.activateConfig(merchantId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("KCB configuration activated successfully", response));
    }

    @PostMapping("/merchants/{merchantId}/activate")
    @Operation(summary = "Activate KCB config for a specific merchant (SUPER_ADMIN only)")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<KcbConfigResponse>> activateConfigForMerchant(
            @PathVariable UUID merchantId,
            @Valid @RequestBody KcbActivateRequest request) {

        KcbConfigResponse response = kcbService.activateConfig(merchantId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("KCB configuration activated successfully", response));
    }

    @GetMapping("/configs")
    @Operation(summary = "Get KCB configs for current merchant (or all for SUPER_ADMIN)")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MERCHANT_ADMIN', 'DISTRIBUTOR_ADMIN', 'CASHIER', 'FINANCE')")
    public ResponseEntity<ApiResponse<List<KcbConfigResponse>>> getConfigs(
            @RequestParam(required = false) UUID merchantId) {

        if (securityUtils.isSuperAdmin() && merchantId == null) {
            return ResponseEntity.ok(ApiResponse.success(kcbService.getAllConfigs()));
        }

        UUID resolvedMerchantId = resolveMerchantId(merchantId);
        return ResponseEntity.ok(ApiResponse.success(kcbService.getConfigs(resolvedMerchantId)));
    }

    @DeleteMapping("/configs/{configId}")
    @Operation(summary = "Deactivate a KCB config")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MERCHANT_ADMIN')")
    public ResponseEntity<ApiResponse<KcbConfigResponse>> deactivateConfig(
            @PathVariable UUID configId) {

        KcbConfigResponse response = kcbService.deactivateConfig(configId);
        return ResponseEntity.ok(ApiResponse.success("KCB configuration deactivated", response));
    }

    @PostMapping("/stk-push")
    @Operation(summary = "Initiate KCB STK push")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MERCHANT_ADMIN', 'DISTRIBUTOR_ADMIN', 'CASHIER', 'FINANCE')")
    public ResponseEntity<ApiResponse<KcbStkPushResponse>> initiateStk(
            @Valid @RequestBody KcbStkPushRequest request) {
        KcbStkPushResponse response = kcbService.initiateStk(request);
        return ResponseEntity.ok(ApiResponse.success("KCB payment prompt sent. Please enter your KCB PIN.", response));
    }

    @GetMapping("/stk-push/{stkRequestId}/status")
    @Operation(summary = "Poll KCB STK push status")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MERCHANT_ADMIN', 'DISTRIBUTOR_ADMIN', 'CASHIER', 'FINANCE')")
    public ResponseEntity<ApiResponse<KcbStkPushResponse>> getStkStatus(
            @PathVariable UUID stkRequestId) {
        KcbStkPushResponse response = kcbService.getStkStatus(stkRequestId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/callback")
    @Operation(summary = "KCB STK callback — public endpoint")
    public ResponseEntity<Void> callback(@RequestBody Map<String, Object> payload) {
        log.info("KCB callback received");
        kcbService.handleCallback(payload);
        return ResponseEntity.ok().build();
    }

    private UUID resolveMerchantId(UUID requestedId) {
        if (requestedId != null) return requestedId;
        UUID fromContext = securityUtils.getCurrentUserMerchantId();
        if (fromContext != null) return fromContext;
        throw new ValidationException("merchantId is required");
    }
}
