package com.zuqi.api.controller;

import com.zuqi.api.dto.ApiResponse;
import com.zuqi.api.dto.ncba.NcbaActivateRequest;
import com.zuqi.api.dto.ncba.NcbaConfigResponse;
import com.zuqi.api.dto.ncba.NcbaStkPushRequest;
import com.zuqi.api.dto.ncba.NcbaStkPushResponse;
import com.zuqi.exception.ValidationException;
import com.zuqi.service.NcbaService;
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
@RequestMapping("/v1/ncba")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "NCBA", description = "NCBA payment configuration and STK push APIs")
public class NcbaController {

    private final NcbaService ncbaService;
    private final SecurityUtils securityUtils;

    @PostMapping("/activate")
    @Operation(summary = "Activate NCBA config for the current merchant")
    public ResponseEntity<ApiResponse<NcbaConfigResponse>> activateConfig(
            @Valid @RequestBody NcbaActivateRequest request) {

        UUID merchantId = resolveMerchantId(null);
        NcbaConfigResponse response = ncbaService.activateConfig(merchantId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("NCBA configuration activated successfully", response));
    }

    @PostMapping("/merchants/{merchantId}/activate")
    @Operation(summary = "Activate NCBA config for a specific merchant (SUPER_ADMIN only)")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<NcbaConfigResponse>> activateConfigForMerchant(
            @PathVariable UUID merchantId,
            @Valid @RequestBody NcbaActivateRequest request) {

        NcbaConfigResponse response = ncbaService.activateConfig(merchantId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("NCBA configuration activated successfully", response));
    }

    @GetMapping("/configs")
    @Operation(summary = "Get NCBA configs for current merchant (or all for SUPER_ADMIN)")
    public ResponseEntity<ApiResponse<List<NcbaConfigResponse>>> getConfigs(
            @RequestParam(required = false) UUID merchantId) {

        if (securityUtils.isSuperAdmin() && merchantId == null) {
            return ResponseEntity.ok(ApiResponse.success(ncbaService.getAllConfigs()));
        }

        UUID resolvedMerchantId = resolveMerchantId(merchantId);
        return ResponseEntity.ok(ApiResponse.success(ncbaService.getConfigs(resolvedMerchantId)));
    }

    @DeleteMapping("/configs/{configId}")
    @Operation(summary = "Deactivate an NCBA config")
    public ResponseEntity<ApiResponse<NcbaConfigResponse>> deactivateConfig(
            @PathVariable UUID configId) {

        NcbaConfigResponse response = ncbaService.deactivateConfig(configId);
        return ResponseEntity.ok(ApiResponse.success("NCBA configuration deactivated", response));
    }

    @PostMapping("/stk-push")
    @Operation(summary = "Initiate NCBA STK push")
    public ResponseEntity<ApiResponse<NcbaStkPushResponse>> initiateStk(
            @Valid @RequestBody NcbaStkPushRequest request) {
        NcbaStkPushResponse response = ncbaService.initiateStk(request);
        return ResponseEntity.ok(ApiResponse.success("NCBA payment prompt sent. Please enter your PIN.", response));
    }

    @GetMapping("/stk-push/{stkRequestId}/status")
    @Operation(summary = "Poll NCBA STK push status")
    public ResponseEntity<ApiResponse<NcbaStkPushResponse>> getStkStatus(
            @PathVariable UUID stkRequestId) {
        NcbaStkPushResponse response = ncbaService.getStkStatus(stkRequestId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/callback")
    @Operation(summary = "NCBA STK callback — public endpoint")
    public ResponseEntity<Void> callback(@RequestBody Map<String, Object> payload) {
        log.info("NCBA callback received");
        ncbaService.handleCallback(payload);
        return ResponseEntity.ok().build();
    }

    private UUID resolveMerchantId(UUID requestedId) {
        if (requestedId != null) return requestedId;
        UUID fromContext = securityUtils.getEffectiveMerchantId();
        if (fromContext != null) return fromContext;
        throw new ValidationException("merchantId is required");
    }
}
