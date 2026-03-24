package com.zuqi.api.controller;

import com.zuqi.api.dto.ApiResponse;
import com.zuqi.api.dto.approval.ApprovalRequestResponse;
import com.zuqi.api.dto.approval.CreateApprovalRequestDto;
import com.zuqi.api.dto.approval.ProcessApprovalRequest;
import com.zuqi.api.dto.common.PageResponse;
import com.zuqi.domain.approval.ApprovalStatus;
import com.zuqi.domain.approval.ApprovalWorkflowType;
import com.zuqi.domain.user.User;
import com.zuqi.service.ApprovalService;
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
@RequestMapping("/v1/approvals")
@RequiredArgsConstructor
@Tag(name = "Approvals", description = "Approval workflow management")
public class ApprovalController {

    private final ApprovalService approvalService;

    @PostMapping
    @Operation(summary = "Create an approval request")
    public ResponseEntity<ApiResponse<ApprovalRequestResponse>> createRequest(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody CreateApprovalRequestDto dto) {
        ApprovalRequestResponse response = approvalService.createRequest(currentUser.getId(), dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Approval request created", response));
    }

    @PostMapping("/{id}/process")
    @Operation(summary = "Approve or reject a request")
    public ResponseEntity<ApiResponse<ApprovalRequestResponse>> processRequest(
            @PathVariable UUID id,
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody ProcessApprovalRequest dto) {
        ApprovalRequestResponse response = approvalService.processRequest(id, currentUser.getId(), dto);
        return ResponseEntity.ok(ApiResponse.success("Request processed", response));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel a pending approval request")
    public ResponseEntity<ApiResponse<ApprovalRequestResponse>> cancelRequest(
            @PathVariable UUID id,
            @AuthenticationPrincipal User currentUser) {
        ApprovalRequestResponse response = approvalService.cancelRequest(id, currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success("Request cancelled", response));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get approval request by ID")
    public ResponseEntity<ApiResponse<ApprovalRequestResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(approvalService.getById(id)));
    }

    @GetMapping
    @Operation(summary = "Get all approval requests with filters")
    public ResponseEntity<ApiResponse<PageResponse<ApprovalRequestResponse>>> getAll(
            @RequestParam(required = false) ApprovalStatus status,
            @RequestParam(required = false) ApprovalWorkflowType workflowType,
            @RequestParam(required = false) String entityType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResponse<ApprovalRequestResponse> result =
                approvalService.getAll(status, workflowType, entityType, page, size);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/my-requests")
    @Operation(summary = "Get current user's approval requests")
    public ResponseEntity<ApiResponse<PageResponse<ApprovalRequestResponse>>> getMyRequests(
            @AuthenticationPrincipal User currentUser,
            @RequestParam(required = false) ApprovalStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResponse<ApprovalRequestResponse> result =
                approvalService.getMyRequests(currentUser.getId(), status, page, size);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/pending")
    @Operation(summary = "Get pending requests awaiting approval")
    public ResponseEntity<ApiResponse<PageResponse<ApprovalRequestResponse>>> getPending(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResponse<ApprovalRequestResponse> result = approvalService.getPendingForApprover(page, size);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/pending/count")
    @Operation(summary = "Count pending approval requests")
    public ResponseEntity<ApiResponse<Long>> countPending() {
        return ResponseEntity.ok(ApiResponse.success(approvalService.countPending()));
    }

    @GetMapping("/entity/{entityType}/{entityId}")
    @Operation(summary = "Get approval requests for a specific entity")
    public ResponseEntity<ApiResponse<List<ApprovalRequestResponse>>> getByEntity(
            @PathVariable String entityType,
            @PathVariable UUID entityId,
            @RequestParam(required = false, defaultValue = "PENDING") ApprovalStatus status) {
        List<ApprovalRequestResponse> result = approvalService.getByEntity(entityType.toUpperCase(), entityId, status);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
