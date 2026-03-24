package com.zuqi.api.dto.approval;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class TransactionalApprovalResponse {
    private UUID entityId;
    private String entityType;
    private String approvalStatus;
    private List<ApprovalRecordResponse> history;

    @Data
    @Builder
    public static class ApprovalRecordResponse {
        private UUID id;
        private int levelNumber;
        private UUID approverId;
        private String approverName;
        private String status;
        private String comment;
        private LocalDateTime createdAt;
    }
}
