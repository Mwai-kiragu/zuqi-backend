package com.zuqi.api.dto.approval;

import com.zuqi.domain.approval.ApprovalWorkflowType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ApprovalWorkflowConfigRequest {

    @NotNull(message = "Workflow type is required")
    private ApprovalWorkflowType workflowType;

    @NotNull(message = "Level number is required")
    @Min(value = 1, message = "Level number must be at least 1")
    private Integer levelNumber;

    @NotBlank(message = "Role label is required")
    private String roleLabel;

    /** Optional Casbin role identifier (informational). */
    private String requiredRole;
}
