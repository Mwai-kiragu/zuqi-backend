package com.zuqi.api.dto.branch;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class SwitchBranchRequest {

    @NotNull(message = "Branch ID is required")
    private UUID branchId;
}
