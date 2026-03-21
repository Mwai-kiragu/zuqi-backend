package com.zuqi.api.dto.returns;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class SalesReturnLineItem {
    @NotNull
    private UUID productId;
    @NotNull @Positive
    private BigDecimal quantity;
    @NotNull @Positive
    private BigDecimal unitPrice;
    private String reason;
}
