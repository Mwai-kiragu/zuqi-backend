package com.zuqi.api.dto.ft;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class FtApprovalDecisionDto {
    private UUID id;
    private int levelNumber;
    private UUID approverId;
    private String approverName;
    private String status; // APPROVED / REJECTED
    private String comment;
    private LocalDateTime createdAt;
}
