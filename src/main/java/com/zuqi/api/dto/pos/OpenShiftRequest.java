package com.zuqi.api.dto.pos;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class OpenShiftRequest {

    @NotNull(message = "Branch ID is required")
    private UUID branchId;

    private UUID terminalId;

    private BigDecimal openingFloat = BigDecimal.ZERO;

    private String notes;
}
