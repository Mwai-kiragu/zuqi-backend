package com.zuqi.api.dto.pos;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class PosSaleItemResponse {

    private UUID id;
    private UUID productId;
    private String productName;
    private String productSku;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal discountAmount;
    private BigDecimal lineTotal;
}
