package com.zuqi.api.dto.accesscontrol;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class UserGroupRequest {

    @NotBlank
    private String name;

    private String description;

    @NotNull
    private UUID userTypeId;

    /** Optional: INITIATOR | VERIFIER | AUTHORIZER */
    private String workflowTier;

    /** Optional ordering within the same workflow tier (1 = first approver, 2 = final sign-off) */
    private Integer approvalLevel;
}
