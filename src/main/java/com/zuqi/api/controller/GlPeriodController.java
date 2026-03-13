package com.zuqi.api.controller;

import com.zuqi.api.dto.ApiResponse;
import com.zuqi.api.dto.gl.GlPeriodRequest;
import com.zuqi.api.dto.gl.GlPeriodResponse;
import com.zuqi.domain.user.User;
import com.zuqi.service.GlPeriodService;
import com.zuqi.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/gl/periods")
@RequiredArgsConstructor
@Tag(name = "GL Periods", description = "Accounting period management")
public class GlPeriodController {

    private final GlPeriodService glPeriodService;
    private final SecurityUtils securityUtils;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MERCHANT_ADMIN','DISTRIBUTOR_ADMIN','FINANCE')")
    @Operation(summary = "Get all accounting periods")
    public ResponseEntity<ApiResponse<List<GlPeriodResponse>>> getAll(
            @RequestParam(required = false) UUID distributorId) {
        UUID effectiveDistributorId = distributorId != null ? distributorId : securityUtils.getDistributorIdForFiltering();
        return ResponseEntity.ok(ApiResponse.success(glPeriodService.getAll(effectiveDistributorId)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MERCHANT_ADMIN','FINANCE')")
    @Operation(summary = "Create an accounting period")
    public ResponseEntity<ApiResponse<GlPeriodResponse>> create(
            @Valid @RequestBody GlPeriodRequest request,
            @RequestParam(required = false) UUID distributorId,
            @AuthenticationPrincipal User currentUser) {
        UUID effectiveDistributorId = distributorId != null ? distributorId : securityUtils.getDistributorIdForFiltering();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Period created", glPeriodService.create(effectiveDistributorId, request, currentUser)));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MERCHANT_ADMIN','FINANCE')")
    @Operation(summary = "Close an accounting period")
    public ResponseEntity<ApiResponse<GlPeriodResponse>> close(
            @PathVariable UUID id,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success("Period closed", glPeriodService.close(id, currentUser)));
    }

    @PostMapping("/{id}/lock")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MERCHANT_ADMIN')")
    @Operation(summary = "Lock an accounting period (irreversible)")
    public ResponseEntity<ApiResponse<GlPeriodResponse>> lock(
            @PathVariable UUID id,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success("Period locked", glPeriodService.lock(id, currentUser)));
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MERCHANT_ADMIN')")
    @Operation(summary = "Reopen a closed accounting period")
    public ResponseEntity<ApiResponse<GlPeriodResponse>> reopen(
            @PathVariable UUID id,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success("Period reopened", glPeriodService.reopen(id, currentUser)));
    }
}
