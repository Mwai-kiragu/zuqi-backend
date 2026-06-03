package com.zuqi.api.dto.inventory;

import jakarta.validation.constraints.DecimalMin;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class StockThresholdRequest {

    @DecimalMin(value = "0", message = "Reorder level must be zero or greater")
    private BigDecimal reorderLevel;
}
