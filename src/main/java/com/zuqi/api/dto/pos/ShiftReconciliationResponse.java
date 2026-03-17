package com.zuqi.api.dto.pos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShiftReconciliationResponse {

    private UUID    shiftId;
    private String  cashierName;
    private String  branchName;
    private LocalDateTime openedAt;

    private BigDecimal openingFloat;
    private int        totalTransactions;
    private BigDecimal totalSales;

    private BigDecimal cashCollected;
    private BigDecimal cardCollected;
    private BigDecimal mpesaCollected;
    private BigDecimal otherCollected;

    /** openingFloat + cashCollected — expected cash in the drawer */
    private BigDecimal expectedCash;
}
