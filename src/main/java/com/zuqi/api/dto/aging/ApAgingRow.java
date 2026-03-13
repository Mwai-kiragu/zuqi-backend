package com.zuqi.api.dto.aging;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class ApAgingRow {
    private UUID supplierId;
    private String supplierName;
    private UUID purchaseOrderId;
    private String poNumber;
    private LocalDate orderDate;
    private LocalDate dueDate;
    private BigDecimal totalAmount;
    private long daysOverdue;
    private BigDecimal current;
    private BigDecimal bucket1;
    private BigDecimal bucket2;
    private BigDecimal bucket3;
    private BigDecimal bucket4;
}
