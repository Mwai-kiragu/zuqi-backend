package com.zuqi.api.dto.supplier;

import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class SupplierBillLineItem {
    private UUID productId;
    private String productName;
    private String description;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal amount;
}
