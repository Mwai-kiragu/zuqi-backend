package com.zuqi.api.dto.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryReportResponse {

    private Long totalProducts;
    private Long lowStockCount;
    private Long outOfStockCount;
    private BigDecimal totalStockValue;
    private List<StockItem> lowStockItems;
    private List<StockItem> outOfStockItems;
    private List<WarehouseSummary> warehouseSummaries;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StockItem {
        private String productId;
        private String productName;
        private String productSku;
        private String warehouseName;
        private BigDecimal quantity;
        private BigDecimal reorderLevel;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WarehouseSummary {
        private String warehouseId;
        private String warehouseName;
        private Long productCount;
        private Long lowStockCount;
        private Long outOfStockCount;
    }
}
