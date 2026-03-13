package com.zuqi.api.dto.inventory;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class StockTakeItemUpdate {

    @NotNull
    @PositiveOrZero(message = "Counted quantity must be zero or positive")
    private BigDecimal countedQuantity;

    private String notes;
}
