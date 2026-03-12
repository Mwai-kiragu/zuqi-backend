package com.zuqi.api.controller;

import com.zuqi.api.dto.ApiResponse;
import com.zuqi.api.dto.mpesa.*;
import com.zuqi.service.MpesaService;
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
import java.util.UUID;

@RestController
@RequestMapping("/v1/mpesa")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "M-Pesa", description = "M-Pesa payment configuration and STK push APIs")
public class MpesaController {

    private final MpesaService mpesaService;
    private final SecurityUtils securityUtils;

    @PostMapping("/activate")
    @Operation(summary = "Activate M-Pesa config", description = "Register a Paybill or Till for the current merchant")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MERCHANT_ADMIN')")
    public ResponseEntity<ApiResponse<MpesaConfigResponse>> activateConfig(
            @Valid @RequestBody MpesaActivateRequest request) {

        UUID merchantId = resolveMerchantId(null);
        MpesaConfigResponse response = mpesaService.activateConfig(merchantId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("M-Pesa configuration activated successfully", response));
    }

    @PostMapping("/merchants/{merchantId}/activate")
    @Operation(summary = "Activate M-Pesa config for a specific merchant (SUPER_ADMIN only)")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<MpesaConfigResponse>> activateConfigForMerchant(
            @PathVariable UUID merchantId,
            @Valid @RequestBody MpesaActivateRequest request) {

        MpesaConfigResponse response = mpesaService.activateConfig(merchantId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("M-Pesa configuration activated successfully", response));
    }

    @GetMapping("/configs")
    @Operation(summary = "Get M-Pesa configs for current merchant (or all for SUPER_ADMIN)")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MERCHANT_ADMIN', 'DISTRIBUTOR_ADMIN', 'CASHIER', 'FINANCE')")
    public ResponseEntity<ApiResponse<List<MpesaConfigResponse>>> getConfigs(
            @RequestParam(required = false) UUID merchantId) {

        if (securityUtils.isSuperAdmin() && merchantId == null) {
            return ResponseEntity.ok(ApiResponse.success(mpesaService.getAllConfigs()));
        }

        UUID resolvedMerchantId = resolveMerchantId(merchantId);
        List<MpesaConfigResponse> configs = mpesaService.getConfigs(resolvedMerchantId);
        return ResponseEntity.ok(ApiResponse.success(configs));
    }

    @DeleteMapping("/configs/{configId}")
    @Operation(summary = "Deactivate an M-Pesa config")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MERCHANT_ADMIN')")
    public ResponseEntity<ApiResponse<MpesaConfigResponse>> deactivateConfig(
            @PathVariable UUID configId) {

        MpesaConfigResponse response = mpesaService.deactivateConfig(configId);
        return ResponseEntity.ok(ApiResponse.success("M-Pesa configuration deactivated", response));
    }

    @PostMapping("/stk-push")
    @Operation(summary = "Initiate STK push", description = "Sends an M-Pesa payment prompt to a customer's phone")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MERCHANT_ADMIN', 'DISTRIBUTOR_ADMIN', 'CASHIER', 'FINANCE')")
    public ResponseEntity<ApiResponse<StkPushResponse>> initiateStk(
            @Valid @RequestBody StkPushRequest request) {

        StkPushResponse response = mpesaService.initiateStk(request);
        return ResponseEntity.ok(ApiResponse.success("STK push initiated. Awaiting customer payment.", response));
    }

    @GetMapping("/stk-push/{stkRequestId}/status")
    @Operation(summary = "Poll STK push status")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MERCHANT_ADMIN', 'DISTRIBUTOR_ADMIN', 'CASHIER', 'FINANCE')")
    public ResponseEntity<ApiResponse<StkPushResponse>> getStkStatus(
            @PathVariable UUID stkRequestId) {

        StkPushResponse response = mpesaService.getStkStatus(stkRequestId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/cash")
    @Operation(summary = "Get cash payment status for current merchant")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MERCHANT_ADMIN', 'DISTRIBUTOR_ADMIN', 'CASHIER', 'FINANCE')")
    public ResponseEntity<ApiResponse<Boolean>> getCashEnabled(
            @RequestParam(required = false) UUID merchantId) {
        UUID resolvedId = resolveMerchantId(merchantId);
        return ResponseEntity.ok(ApiResponse.success(mpesaService.getCashEnabled(resolvedId)));
    }

    @PatchMapping("/cash")
    @Operation(summary = "Enable or disable cash payments for current merchant")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MERCHANT_ADMIN')")
    public ResponseEntity<ApiResponse<Boolean>> setCashEnabled(
            @RequestParam(required = false) UUID merchantId,
            @RequestBody java.util.Map<String, Boolean> body) {
        UUID resolvedId = resolveMerchantId(merchantId);
        boolean enabled = Boolean.TRUE.equals(body.get("enabled"));
        return ResponseEntity.ok(ApiResponse.success(mpesaService.setCashEnabled(resolvedId, enabled)));
    }

    @PostMapping("/callback")
    @Operation(summary = "M-Pesa STK callback", description = "Receives payment confirmation from the Daraja gateway. Public endpoint.")
    public ResponseEntity<Void> stkCallback(@RequestBody StkCallbackPayload payload) {
        log.info("STK callback received: checkoutId={} resultCode={}",
                payload.checkoutRequestId(), payload.resultCode());
        mpesaService.handleStkCallback(payload);
        return ResponseEntity.ok().build();
    }

    private UUID resolveMerchantId(UUID requestedMerchantId) {
        if (requestedMerchantId != null) return requestedMerchantId;
        UUID fromContext = securityUtils.getCurrentUserMerchantId();
        if (fromContext != null) return fromContext;
        throw new com.zuqi.exception.ValidationException("merchantId is required");
    }
}
