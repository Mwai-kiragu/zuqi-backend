package com.zuqi.api.dto.approval;

import com.zuqi.domain.approval.ApprovalStatus;
import com.zuqi.domain.approval.ApprovalWorkflowType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalRequestResponse {
    private UUID id;
    private String requestNumber;
    private ApprovalWorkflowType workflowType;
    private String workflowTypeLabel;
    private String entityType;
    private UUID entityId;
    private String entityName;
    private UUID requestedById;
    private String requestedByEmail;
    private String requestedByName;
    private ApprovalStatus status;
    private String description;
    private Map<String, Object> currentValues;
    private Map<String, Object> requestedValues;
    private Integer requiredApprovals;
    private Integer receivedApprovals;
    private BigDecimal amount;
    private String rejectionReason;
    private LocalDateTime approvedAt;
    private LocalDateTime rejectedAt;
    private LocalDateTime expiresAt;
    private List<ApprovalActionResponse> actions;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
