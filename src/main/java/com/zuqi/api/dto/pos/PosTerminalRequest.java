package com.zuqi.api.dto.pos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class PosTerminalRequest {

    @NotNull(message = "Branch ID is required")
    private UUID branchId;

    @NotBlank(message = "Terminal name is required")
    private String name;

    private String code;
}
