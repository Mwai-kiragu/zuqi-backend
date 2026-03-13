package com.zuqi.api.dto.ft;

import lombok.Data;

import java.util.UUID;

@Data
public class FtApprovalLevelDto {
    private UUID id;
    private int levelNumber;
    private String levelName;
    private UUID approverUserId;
    private String approverName; // populated separately
}
