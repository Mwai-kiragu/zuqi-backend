package com.zuqi.api.dto.pos;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateSaleRequest {

    @NotNull(message = "Branch ID is required")
    private UUID branchId;

    private UUID shiftId;

    private UUID customerId;

    private String customerName;

    private String customerPhone;

    private String notes;
}
