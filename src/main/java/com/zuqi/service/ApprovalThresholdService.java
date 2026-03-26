package com.zuqi.service;

import com.zuqi.api.dto.approvalthreshold.ApprovalThresholdRequest;
import com.zuqi.api.dto.approvalthreshold.ApprovalThresholdResponse;
import com.zuqi.domain.approval.ApprovalWorkflowType;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface ApprovalThresholdService {

    List<ApprovalThresholdResponse> getAll(UUID distributorId);

    ApprovalThresholdResponse getById(UUID id);

    ApprovalThresholdResponse create(UUID distributorId, ApprovalThresholdRequest request);

    ApprovalThresholdResponse update(UUID id, ApprovalThresholdRequest request);

    void deactivate(UUID id);

    /**
     * Returns the required number of approvals for the given distributor, workflow type, and amount.
     * Defaults to 1 if no matching threshold is configured.
     */
    int getRequiredApprovals(UUID distributorId, ApprovalWorkflowType workflowType, BigDecimal amount);
}
