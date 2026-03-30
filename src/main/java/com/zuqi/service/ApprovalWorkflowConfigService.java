package com.zuqi.service;

import com.zuqi.api.dto.approval.ApprovalWorkflowConfigRequest;
import com.zuqi.api.dto.approval.ApprovalWorkflowConfigResponse;
import com.zuqi.domain.approval.ApprovalWorkflowType;

import java.util.List;
import java.util.UUID;

public interface ApprovalWorkflowConfigService {

    /** All configs for a distributor, ordered by workflowType then level. */
    List<ApprovalWorkflowConfigResponse> getAll(UUID distributorId);

    /** Active levels for a specific workflow type. */
    List<ApprovalWorkflowConfigResponse> getByWorkflowType(UUID distributorId, ApprovalWorkflowType type);

    /** Count of active levels → determines requiredApprovals for new requests. Returns 0 if not configured. */
    int countActiveLevels(UUID distributorId, ApprovalWorkflowType type);

    ApprovalWorkflowConfigResponse create(UUID distributorId, ApprovalWorkflowConfigRequest request);

    ApprovalWorkflowConfigResponse update(UUID id, ApprovalWorkflowConfigRequest request);

    void delete(UUID id);
}
