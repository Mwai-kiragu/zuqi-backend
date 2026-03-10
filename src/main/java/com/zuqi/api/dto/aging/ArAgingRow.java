package com.zuqi.api.dto.aging;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class ArAgingRow {
    private UUID customerId;
    private String customerName;
    private UUID invoiceId;
    private String invoiceNumber;
    private LocalDate issueDate;
    private LocalDate dueDate;
    private BigDecimal totalAmount;
    private BigDecimal balanceDue;
    private long daysOverdue;
    // Bucketed amounts (only one will be non-zero per row)
    private BigDecimal current;
    private BigDecimal bucket1;
    private BigDecimal bucket2;
    private BigDecimal bucket3;
    private BigDecimal bucket4;
}
