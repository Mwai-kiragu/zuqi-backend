package com.zuqi.api.dto.returns;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalesReturnStatsResponse {
    private long totalCount;
    private long pendingCount;
    private long confirmedCount;
    private long cancelledCount;
    private BigDecimal totalValue;
}
