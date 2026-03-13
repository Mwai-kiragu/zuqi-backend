package com.zuqi.api.dto.branch;

import com.zuqi.domain.branch.BranchUserStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class BranchUserResponse {

    private UUID id;
    private UUID branchId;
    private String branchName;
    private UUID userId;
    private String userName;
    private String userEmail;
    private String role;
    private BranchUserStatus status;
    private LocalDateTime createdAt;
}
