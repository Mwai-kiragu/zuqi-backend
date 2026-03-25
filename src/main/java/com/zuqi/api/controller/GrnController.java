package com.zuqi.api.controller;

import com.zuqi.api.dto.ApiResponse;
import com.zuqi.api.dto.procurement.GrnRequest;
import com.zuqi.api.dto.procurement.GrnResponse;
import com.zuqi.domain.procurement.GrnStatus;
import com.zuqi.domain.user.User;
import com.zuqi.service.GrnService;
import com.zuqi.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/grns")
@RequiredArgsConstructor
@Tag(name = "GRN", description = "Goods Receipt Note management")
public class GrnController {

    private final GrnService grnService;
    private final SecurityUtils securityUtils;

    @GetMapping
    @Operation(summary = "List GRNs with optional filters")
    public ResponseEntity<ApiResponse<Page<GrnResponse>>> list(
            @RequestParam(required = false) UUID distributorId,
            @RequestParam(required = false) GrnStatus status,
            @RequestParam(required = false) UUID supplierId,
            @RequestParam(required = false) UUID purchaseOrderId,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                grnService.getGrns(distributorId, status, supplierId, purchaseOrderId, pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a GRN by ID")
    public ResponseEntity<ApiResponse<GrnResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(grnService.getGrnById(id)));
    }

    @PostMapping
    @Operation(summary = "Create a new GRN against a PO")
    public ResponseEntity<ApiResponse<GrnResponse>> create(
            @Valid @RequestBody GrnRequest request) {
        User currentUser = securityUtils.getCurrentUser();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(grnService.createGrn(request, currentUser)));
    }

    @PostMapping("/{id}/confirm")
    @Operation(summary = "Confirm a GRN — updates stock quantities in the warehouse")
    public ResponseEntity<ApiResponse<GrnResponse>> confirm(@PathVariable UUID id) {
        User currentUser = securityUtils.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.success(grnService.confirmGrn(id, currentUser)));
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Reject a GRN delivery — no stock update")
    public ResponseEntity<ApiResponse<GrnResponse>> reject(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        User currentUser = securityUtils.getCurrentUser();
        String reason = body.getOrDefault("reason", "");
        return ResponseEntity.ok(ApiResponse.success(grnService.rejectGrn(id, reason, currentUser)));
    }
}
