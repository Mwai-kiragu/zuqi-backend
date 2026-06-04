package com.zuqi.api.dto.approval;

import com.zuqi.domain.approval.ApprovalWorkflowType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateApprovalRequestDto {

    @NotNull(message = "Workflow type is required")
    private ApprovalWorkflowType workflowType;

    @NotBlank(message = "Entity type is required")
    private String entityType;

    private UUID entityId;

    private String entityName;

    @NotBlank(message = "Description is required")
    private String description;

    private Map<String, Object> currentValues;

    @NotNull(message = "Requested values are required")
    private Map<String, Object> requestedValues;

    private BigDecimal amount;

    private Integer requiredApprovals;

    /** Optional explicit distributor scope — overrides requester's distributorId when set. */
    private UUID distributorId;
}
