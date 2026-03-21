package com.zuqi.api.dto.pricing;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class PriceListItemRequest {
    @NotNull private UUID productId;
    @NotNull @Positive private BigDecimal unitPrice;
    private BigDecimal discountPercent;
}
