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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/gl/periods")
@RequiredArgsConstructor
@Tag(name = "GL Periods", description = "Accounting period management")
public class GlPeriodController {

    private final GlPeriodService glPeriodService;
    private final SecurityUtils securityUtils;

    @GetMapping
    @Operation(summary = "Get all accounting periods")
    public ResponseEntity<ApiResponse<List<GlPeriodResponse>>> getAll(
            @RequestParam(required = false) UUID distributorId) {
        UUID effectiveDistributorId = distributorId != null ? distributorId : securityUtils.getDistributorIdForFiltering();
        return ResponseEntity.ok(ApiResponse.success(glPeriodService.getAll(effectiveDistributorId)));
    }

    @PostMapping
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
    @Operation(summary = "Manually close a LOCKED period (after month-end checklist)")
    public ResponseEntity<ApiResponse<GlPeriodResponse>> close(
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, String> body,
            @AuthenticationPrincipal User currentUser) {
        String notes = body != null ? body.get("closedNotes") : null;
        return ResponseEntity.ok(ApiResponse.success("Period closed", glPeriodService.close(id, notes, currentUser)));
    }

    @PostMapping("/{id}/lock")
    @Operation(summary = "Manually lock an OPEN period (blocks all posting)")
    public ResponseEntity<ApiResponse<GlPeriodResponse>> lock(
            @PathVariable UUID id,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success("Period locked", glPeriodService.lock(id, currentUser)));
    }

    @PostMapping("/{id}/reopen")
    @Operation(summary = "Reopen a LOCKED period to allow new postings")
    public ResponseEntity<ApiResponse<GlPeriodResponse>> reopen(
            @PathVariable UUID id,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success("Period reopened", glPeriodService.reopen(id, currentUser)));
    }

    @PostMapping("/auto-lock")
    @Operation(summary = "Trigger auto-lock sweep (normally runs on scheduler)")
    public ResponseEntity<ApiResponse<Void>> triggerAutoLock() {
        int count = glPeriodService.autoLockExpiredPeriods();
        String msg = count + " period(s) auto-locked";
        return ResponseEntity.ok(ApiResponse.success(msg));
    }
}
