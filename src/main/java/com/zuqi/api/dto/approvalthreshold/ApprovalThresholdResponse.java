package com.zuqi.api.dto.approvalthreshold;

import com.zuqi.domain.approval.ApprovalWorkflowType;
import com.zuqi.domain.approvalthreshold.ApprovalThreshold;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class ApprovalThresholdResponse {

    private UUID id;
    private UUID distributorId;
    private ApprovalWorkflowType workflowType;
    private BigDecimal minAmount;
    private BigDecimal maxAmount;
    private int requiredApprovals;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ApprovalThresholdResponse from(ApprovalThreshold t) {
        return ApprovalThresholdResponse.builder()
                .id(t.getId())
                .distributorId(t.getDistributorId())
                .workflowType(t.getWorkflowType())
                .minAmount(t.getMinAmount())
                .maxAmount(t.getMaxAmount())
                .requiredApprovals(t.getRequiredApprovals())
                .active(t.isActive())
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .build();
    }
}
