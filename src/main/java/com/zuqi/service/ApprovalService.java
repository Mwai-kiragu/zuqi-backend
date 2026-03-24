package com.zuqi.service;

import com.zuqi.api.dto.approval.ApprovalRequestResponse;
import com.zuqi.api.dto.approval.CreateApprovalRequestDto;
import com.zuqi.api.dto.approval.ProcessApprovalRequest;
import com.zuqi.api.dto.common.PageResponse;
import com.zuqi.domain.approval.ApprovalStatus;
import com.zuqi.domain.approval.ApprovalWorkflowType;

import java.util.List;
import java.util.UUID;

public interface ApprovalService {

    ApprovalRequestResponse createRequest(UUID requesterId, CreateApprovalRequestDto dto);

    ApprovalRequestResponse processRequest(UUID requestId, UUID approverId, ProcessApprovalRequest dto);

    ApprovalRequestResponse cancelRequest(UUID requestId, UUID requesterId);

    ApprovalRequestResponse getById(UUID requestId);

    PageResponse<ApprovalRequestResponse> getAll(ApprovalStatus status, ApprovalWorkflowType workflowType,
                                                   String entityType, int page, int size);

    PageResponse<ApprovalRequestResponse> getMyRequests(UUID requesterId, ApprovalStatus status, int page, int size);

    PageResponse<ApprovalRequestResponse> getPendingForApprover(int page, int size);

    long countPending();

    void expireStaleRequests();

    List<ApprovalRequestResponse> getByEntity(String entityType, UUID entityId, ApprovalStatus status);
}
