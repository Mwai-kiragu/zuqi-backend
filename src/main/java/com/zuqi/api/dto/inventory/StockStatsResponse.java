package com.zuqi.api.dto.inventory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockStatsResponse {
    private long totalCount;
    private long healthyCount;
    private long lowStockCount;
    private long outOfStockCount;
}
