package com.zuqi.api.dto.inventory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class StockTransferRequest {

    @NotNull(message = "Source warehouse ID is required")
    private UUID sourceWarehouseId;

    @NotNull(message = "Destination warehouse ID is required")
    private UUID destinationWarehouseId;

    private UUID sourceBranchId;

    private UUID destinationBranchId;

    private String notes;

    @NotEmpty(message = "At least one item is required")
    @Valid
    private List<StockTransferItemRequest> items;
}
