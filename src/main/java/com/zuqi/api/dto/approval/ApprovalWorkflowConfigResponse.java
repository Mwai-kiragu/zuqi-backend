package com.zuqi.api.dto.approval;

import com.zuqi.domain.approval.ApprovalWorkflowType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class ApprovalWorkflowConfigResponse {
    private UUID id;
    private UUID distributorId;
    private ApprovalWorkflowType workflowType;
    private Integer levelNumber;
    private String roleLabel;
    private String requiredRole;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
