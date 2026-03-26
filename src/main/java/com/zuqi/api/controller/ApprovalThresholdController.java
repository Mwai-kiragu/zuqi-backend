package com.zuqi.api.controller;

import com.zuqi.api.dto.approvalthreshold.ApprovalThresholdRequest;
import com.zuqi.api.dto.approvalthreshold.ApprovalThresholdResponse;
import com.zuqi.api.dto.ApiResponse;
import com.zuqi.service.ApprovalThresholdService;
import com.zuqi.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/approval-thresholds")
@RequiredArgsConstructor
public class ApprovalThresholdController {

    private final ApprovalThresholdService approvalThresholdService;
    private final SecurityUtils securityUtils;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ApprovalThresholdResponse>>> getAll() {
        UUID distributorId = securityUtils.getCurrentUserDistributorId();
        return ResponseEntity.ok(ApiResponse.success(approvalThresholdService.getAll(distributorId)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ApprovalThresholdResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(approvalThresholdService.getById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ApprovalThresholdResponse>> create(
            @Valid @RequestBody ApprovalThresholdRequest request) {
        UUID distributorId = securityUtils.getCurrentUserDistributorId();
        return ResponseEntity.ok(ApiResponse.success(approvalThresholdService.create(distributorId, request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ApprovalThresholdResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody ApprovalThresholdRequest request) {
        return ResponseEntity.ok(ApiResponse.success(approvalThresholdService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable UUID id) {
        approvalThresholdService.deactivate(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
