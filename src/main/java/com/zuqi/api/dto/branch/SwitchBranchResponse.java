package com.zuqi.api.dto.branch;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class SwitchBranchResponse {

    private String accessToken;
    private long expiresIn;
    private UUID branchId;
    private String branchName;
}
