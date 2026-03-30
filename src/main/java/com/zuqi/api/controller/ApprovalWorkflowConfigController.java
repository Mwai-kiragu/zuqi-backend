package com.zuqi.api.controller;

import com.zuqi.api.dto.approval.ApprovalWorkflowConfigRequest;
import com.zuqi.api.dto.approval.ApprovalWorkflowConfigResponse;
import com.zuqi.api.dto.ApiResponse;
import com.zuqi.domain.approval.ApprovalWorkflowType;
import com.zuqi.service.ApprovalWorkflowConfigService;
import com.zuqi.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/approval-configs")
@RequiredArgsConstructor
public class ApprovalWorkflowConfigController {

    private final ApprovalWorkflowConfigService service;
    private final SecurityUtils securityUtils;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ApprovalWorkflowConfigResponse>>> getAll(
            @RequestParam(required = false) ApprovalWorkflowType workflowType) {
        UUID distributorId = securityUtils.getCurrentUserDistributorId();
        if (workflowType != null) {
            return ResponseEntity.ok(ApiResponse.success(service.getByWorkflowType(distributorId, workflowType)));
        }
        return ResponseEntity.ok(ApiResponse.success(service.getAll(distributorId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ApprovalWorkflowConfigResponse>> create(
            @Valid @RequestBody ApprovalWorkflowConfigRequest request) {
        UUID distributorId = securityUtils.getCurrentUserDistributorId();
        return ResponseEntity.ok(ApiResponse.success(service.create(distributorId, request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ApprovalWorkflowConfigResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody ApprovalWorkflowConfigRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
