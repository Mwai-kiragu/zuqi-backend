package com.zuqi.api.dto.approval;

import com.zuqi.domain.approval.ApprovalDecision;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalActionResponse {
    private UUID id;
    private UUID approverId;
    private String approverEmail;
    private String approverName;
    private ApprovalDecision decision;
    private Integer approvalLevel;
    private String comments;
    private LocalDateTime actionAt;
}
