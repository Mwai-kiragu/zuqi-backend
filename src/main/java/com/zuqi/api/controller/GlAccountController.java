package com.zuqi.api.controller;

import com.zuqi.api.dto.ApiResponse;
import com.zuqi.api.dto.gl.GlAccountRequest;
import com.zuqi.api.dto.gl.GlAccountResponse;
import com.zuqi.domain.user.User;
import com.zuqi.service.GlAccountService;
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
@RequestMapping("/v1/gl/accounts")
@RequiredArgsConstructor
@Tag(name = "GL Accounts", description = "Chart of Accounts management")
public class GlAccountController {

    private final GlAccountService glAccountService;
    private final SecurityUtils securityUtils;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DISTRIBUTOR_ADMIN','FINANCE')")
    @Operation(summary = "Get all GL accounts")
    public ResponseEntity<ApiResponse<List<GlAccountResponse>>> getAll(
            @RequestParam(required = false) UUID distributorId) {
        UUID effectiveDistributorId = distributorId != null ? distributorId : securityUtils.getDistributorIdForFiltering();
        return ResponseEntity.ok(ApiResponse.success(glAccountService.getAll(effectiveDistributorId)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DISTRIBUTOR_ADMIN','FINANCE')")
    @Operation(summary = "Get GL account by ID")
    public ResponseEntity<ApiResponse<GlAccountResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(glAccountService.getById(id)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','FINANCE')")
    @Operation(summary = "Create a GL account")
    public ResponseEntity<ApiResponse<GlAccountResponse>> create(
            @Valid @RequestBody GlAccountRequest request,
            @RequestParam(required = false) UUID distributorId,
            @AuthenticationPrincipal User currentUser) {
        UUID effectiveDistributorId = distributorId != null ? distributorId : securityUtils.getDistributorIdForFiltering();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("GL account created", glAccountService.create(effectiveDistributorId, request, currentUser)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','FINANCE')")
    @Operation(summary = "Update a GL account")
    public ResponseEntity<ApiResponse<GlAccountResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody GlAccountRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success("GL account updated", glAccountService.update(id, request, currentUser)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','FINANCE')")
    @Operation(summary = "Deactivate a GL account")
    public ResponseEntity<ApiResponse<Void>> deactivate(
            @PathVariable UUID id,
            @AuthenticationPrincipal User currentUser) {
        glAccountService.deactivate(id, currentUser);
        return ResponseEntity.ok(ApiResponse.success("GL account deactivated"));
    }
}
