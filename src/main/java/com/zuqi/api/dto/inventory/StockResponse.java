package com.zuqi.api.dto.inventory;

import com.fasterxml.jackson.annotation.JsonInclude;
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
public class StockResponse {

    private UUID id;
    private UUID distributorId;
    private String distributorName;
    private UUID warehouseId;
    private String warehouseName;
    private String warehouseCode;
    private UUID productId;
    private String productName;
    private String productSku;
    private BigDecimal quantity;
    private BigDecimal reservedQuantity;
    private BigDecimal availableQuantity;
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private BigDecimal reorderLevel;
    private boolean lowStock;
    private LocalDateTime lastStockCheck;
    private LocalDateTime updatedAt;

    // AI prediction fields (populated by StockoutPredictor on list endpoints)
    private Double aiRiskScore;
    private Double aiDaysUntilStockout;
    private Double aiDemand7d;
}
