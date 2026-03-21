package com.zuqi.api.controller;

import com.zuqi.api.dto.ApiResponse;
import com.zuqi.api.dto.approval.TransactionalApprovalRequest;
import com.zuqi.api.dto.approval.TransactionalApprovalResponse;
import com.zuqi.domain.user.User;
import com.zuqi.service.TransactionalApprovalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Transactional approval controller.
 * Handles submit / approve / reject for ORDER, STOCK_TRANSFER, SUPPLIER_BILL, FUNDS_TRANSFER.
 *
 * Endpoints:
 *   POST /v1/approvals/transactional/{entityType}/{entityId}/submit
 *   POST /v1/approvals/transactional/{entityType}/{entityId}/approve
 *   POST /v1/approvals/transactional/{entityType}/{entityId}/reject
 *   GET  /v1/approvals/transactional/{entityType}/{entityId}/history
 */
@RestController
@RequestMapping("/v1/approvals/transactional")
@RequiredArgsConstructor
@Tag(name = "Transactional Approvals", description = "Submit / approve / reject transactional entities")
public class TransactionalApprovalController {

    private final TransactionalApprovalService transactionalApprovalService;

    @PostMapping("/{entityType}/{entityId}/submit")
    @Operation(summary = "Submit an entity for VERIFIER review")
    public ResponseEntity<ApiResponse<TransactionalApprovalResponse>> submit(
            @PathVariable String entityType,
            @PathVariable UUID entityId,
            @AuthenticationPrincipal User currentUser) {
        TransactionalApprovalResponse response =
                transactionalApprovalService.submit(entityType, entityId, currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success("Submitted for verification", response));
    }

    @PostMapping("/{entityType}/{entityId}/approve")
    @Operation(summary = "Approve the current pending level")
    public ResponseEntity<ApiResponse<TransactionalApprovalResponse>> approve(
            @PathVariable String entityType,
            @PathVariable UUID entityId,
            @RequestBody(required = false) TransactionalApprovalRequest body,
            @AuthenticationPrincipal User currentUser) {
        String comment = body != null ? body.getComment() : null;
        TransactionalApprovalResponse response =
                transactionalApprovalService.approve(entityType, entityId, currentUser.getId(), comment);
        return ResponseEntity.ok(ApiResponse.success("Approved successfully", response));
    }

    @PostMapping("/{entityType}/{entityId}/reject")
    @Operation(summary = "Reject the pending approval")
    public ResponseEntity<ApiResponse<TransactionalApprovalResponse>> reject(
            @PathVariable String entityType,
            @PathVariable UUID entityId,
            @RequestBody TransactionalApprovalRequest body,
            @AuthenticationPrincipal User currentUser) {
        TransactionalApprovalResponse response =
                transactionalApprovalService.reject(entityType, entityId, currentUser.getId(), body.getComment());
        return ResponseEntity.ok(ApiResponse.success("Rejected", response));
    }

    @GetMapping("/{entityType}/{entityId}/history")
    @Operation(summary = "Get approval history for an entity")
    public ResponseEntity<ApiResponse<TransactionalApprovalResponse>> history(
            @PathVariable String entityType,
            @PathVariable UUID entityId) {
        TransactionalApprovalResponse response =
                transactionalApprovalService.getHistory(entityType, entityId);
        return ResponseEntity.ok(ApiResponse.success("Approval history", response));
    }
}
