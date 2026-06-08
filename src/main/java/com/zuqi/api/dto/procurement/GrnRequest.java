package com.zuqi.api.dto.procurement;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class GrnRequest {

    @NotNull(message = "Purchase order is required")
    private UUID purchaseOrderId;

    @NotNull(message = "Warehouse is required")
    private UUID warehouseId;

    @NotBlank(message = "Supplier delivery note number is required")
    private String deliveryNoteNumber;

    private String notes;

    @NotEmpty(message = "At least one item is required")
    @Valid
    private List<GrnItemDto> items;
}
