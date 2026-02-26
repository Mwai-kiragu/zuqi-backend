package com.zuqi.api.dto.approval;

import com.zuqi.domain.approval.ApprovalDecision;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessApprovalRequest {

    @NotNull(message = "Decision is required")
    private ApprovalDecision decision;

    private String comments;
}
