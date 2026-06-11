package com.zuqi.api.dto.pos;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class PosSummaryResponse {

    private UUID branchId;
    private String branchName;
    private LocalDate date;
    private long totalTransactions;
    private long completedTransactions;
    private long cancelledTransactions;
    private BigDecimal totalRevenue;
    private BigDecimal totalDiscounts;
    private BigDecimal averageTransactionValue;

    private long unpaidCount;
    private BigDecimal unpaidTotal;
    private long partiallyPaidCount;
    private BigDecimal partiallyPaidBalanceDue;
    private long refundedCount;
    private BigDecimal refundedTotal;
}
