package com.zuqi.api.dto.supplier;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class SupplierBillLineItem {
    private String description;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal amount;
}
