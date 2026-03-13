package com.zuqi.api.dto.branch;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class BranchUserRequest {

    @NotNull(message = "User ID is required")
    private UUID userId;

    private String role;
}
