package com.zuqi.api.dto.inventory;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class StockTakeItemResponse {

    private UUID id;
    private UUID productId;
    private String productName;
    private BigDecimal systemQuantity;
    private BigDecimal countedQuantity;
    private BigDecimal variance;
    private String notes;
}
