package com.zuqi.api.dto.pos;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class SaleItemRequest {

    @NotNull(message = "Product ID is required")
    private UUID productId;

    @NotNull
    @Positive(message = "Quantity must be positive")
    private BigDecimal quantity;

    @NotNull
    @Positive(message = "Unit price must be positive")
    private BigDecimal unitPrice;

    private BigDecimal discountAmount = BigDecimal.ZERO;
}
