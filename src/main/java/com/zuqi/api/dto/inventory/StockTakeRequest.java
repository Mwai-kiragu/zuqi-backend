package com.zuqi.api.dto.inventory;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class StockTakeRequest {

    @NotNull(message = "Warehouse ID is required")
    private UUID warehouseId;

    private UUID branchId;

    private String notes;
}
