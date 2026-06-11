package com.zuqi.api.controller;

import com.zuqi.api.dto.ApiResponse;
import com.zuqi.api.dto.gl.CostCenterBulkItemRequest;
import com.zuqi.api.dto.gl.CostCenterBulkResponse;
import com.zuqi.api.dto.gl.CostCenterRequest;
import com.zuqi.api.dto.gl.CostCenterResponse;
import com.zuqi.domain.user.User;
import com.zuqi.service.CostCenterService;
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
import java.util.UUID;

@RestController
@RequestMapping("/v1/gl/cost-centers")
@RequiredArgsConstructor
@Tag(name = "Cost Centers", description = "Cost centre management")
public class CostCenterController {

    private final CostCenterService costCenterService;
    private final SecurityUtils securityUtils;

    @GetMapping
    @Operation(summary = "Get all cost centers")
    public ResponseEntity<ApiResponse<List<CostCenterResponse>>> getAll(
            @RequestParam(required = false) UUID distributorId) {
        UUID effectiveDistributorId = distributorId != null ? distributorId : securityUtils.getDistributorIdForFiltering();
        return ResponseEntity.ok(ApiResponse.success(costCenterService.getAll(effectiveDistributorId)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get cost center by ID")
    public ResponseEntity<ApiResponse<CostCenterResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(costCenterService.getById(id)));
    }

    @PostMapping
    @Operation(summary = "Create a cost center")
    public ResponseEntity<ApiResponse<CostCenterResponse>> create(
            @Valid @RequestBody CostCenterRequest request,
            @RequestParam(required = false) UUID distributorId,
            @AuthenticationPrincipal User currentUser) {
        UUID effectiveDistributorId = distributorId != null ? distributorId : securityUtils.getDistributorIdForFiltering();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Cost center created", costCenterService.create(effectiveDistributorId, request, currentUser)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a cost center")
    public ResponseEntity<ApiResponse<CostCenterResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody CostCenterRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success("Cost center updated", costCenterService.update(id, request, currentUser)));
    }

    @PostMapping("/bulk")
    @Operation(summary = "Bulk import cost centers from CSV/spreadsheet data")
    public ResponseEntity<ApiResponse<CostCenterBulkResponse>> bulkCreate(
            @RequestBody List<CostCenterBulkItemRequest> items,
            @RequestParam(required = false) UUID distributorId,
            @AuthenticationPrincipal User currentUser) {
        UUID effectiveDistributorId = distributorId != null ? distributorId : securityUtils.getDistributorIdForFiltering();
        CostCenterBulkResponse result = costCenterService.bulkCreate(effectiveDistributorId, items, currentUser);
        String message = result.getCreated() + " cost centre(s) imported";
        if (result.getSkipped() > 0) message += ", " + result.getSkipped() + " skipped";
        return ResponseEntity.ok(ApiResponse.success(message, result));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Deactivate a cost center")
    public ResponseEntity<ApiResponse<Void>> deactivate(
            @PathVariable UUID id,
            @AuthenticationPrincipal User currentUser) {
        costCenterService.deactivate(id, currentUser);
        return ResponseEntity.ok(ApiResponse.success("Cost center deactivated"));
    }
}
