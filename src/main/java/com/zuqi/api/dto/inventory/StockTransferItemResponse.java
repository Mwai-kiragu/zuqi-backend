package com.zuqi.api.dto.inventory;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class StockTransferItemResponse {

    private UUID id;
    private UUID productId;
    private String productName;
    private BigDecimal requestedQuantity;
    private BigDecimal receivedQuantity;
    private String notes;
}
