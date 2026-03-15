package com.zuqi.api.dto.pos;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class ShiftReconciliationResponse {

    private UUID shiftId;
    private String cashierName;
    private String branchName;
    private LocalDateTime openedAt;
    private BigDecimal openingFloat;

    private long totalTransactions;
    private BigDecimal totalSales;

    private BigDecimal cashCollected;
    private BigDecimal cardCollected;
    private BigDecimal mpesaCollected;
    private BigDecimal otherCollected;

    /** openingFloat + cashCollected — the amount of cash the drawer should hold */
    private BigDecimal expectedCash;
}
