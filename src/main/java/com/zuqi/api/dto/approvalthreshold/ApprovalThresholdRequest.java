package com.zuqi.api.dto.approvalthreshold;

import com.zuqi.domain.approval.ApprovalWorkflowType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ApprovalThresholdRequest {

    @NotNull
    private ApprovalWorkflowType workflowType;

    @NotNull
    private BigDecimal minAmount;

    private BigDecimal maxAmount;

    @Min(1)
    private int requiredApprovals = 1;
}
