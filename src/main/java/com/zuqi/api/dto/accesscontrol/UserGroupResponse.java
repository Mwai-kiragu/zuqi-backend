package com.zuqi.api.dto.accesscontrol;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class UserGroupResponse {
    private UUID id;
    private String name;
    private String description;
    private UUID distributorId;
    private UUID userTypeId;
    private String userTypeName;
    private String userTypeBaseRole;
    private String workflowTier;
    private Integer approvalLevel;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
