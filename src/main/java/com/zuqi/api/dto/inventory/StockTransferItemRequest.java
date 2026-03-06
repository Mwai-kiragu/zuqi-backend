package com.zuqi.api.dto.inventory;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class StockTransferItemRequest {

    @NotNull(message = "Product ID is required")
    private UUID productId;

    @NotNull
    @Positive(message = "Quantity must be positive")
    private BigDecimal requestedQuantity;

    private String notes;
}
