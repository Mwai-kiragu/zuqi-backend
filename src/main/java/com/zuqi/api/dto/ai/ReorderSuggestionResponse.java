package com.zuqi.api.dto.ai;

import com.zuqi.domain.ai.ReorderSuggestion;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReorderSuggestionResponse {

    private UUID id;
    private UUID distributorId;
    private UUID warehouseId;
    private String warehouseName;
    private UUID productId;
    private String productName;
    private String productSku;
    private UUID supplierId;
    private Double suggestedQuantity;
    private Double economicOrderQuantity;
    private Double safetyStockLevel;
    private Double reorderPoint;
    private Double currentStock;
    private Double daysOfSupplyRemaining;
    private Double avgDailyDemand;
    private Double leadTimeDays;
    private Double confidenceScore;
    private String dataPhase;
    private String status;
    private Integer modelVersion;
    private UUID convertedPrId;
    private LocalDateTime computedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ReorderSuggestionResponse fromEntity(ReorderSuggestion rs) {
        return ReorderSuggestionResponse.builder()
                .id(rs.getId())
                .distributorId(rs.getDistributor() != null ? rs.getDistributor().getId() : null)
                .warehouseId(rs.getWarehouse() != null ? rs.getWarehouse().getId() : null)
                .warehouseName(rs.getWarehouse() != null ? rs.getWarehouse().getName() : null)
                .productId(rs.getProduct() != null ? rs.getProduct().getId() : null)
                .productName(rs.getProduct() != null ? rs.getProduct().getName() : null)
                .productSku(rs.getProduct() != null ? rs.getProduct().getSku() : null)
                .supplierId(rs.getSupplierId())
                .suggestedQuantity(rs.getSuggestedQty())
                .economicOrderQuantity(rs.getEconomicOrderQty())
                .safetyStockLevel(rs.getSafetyStock())
                .reorderPoint(rs.getReorderPoint())
                .currentStock(rs.getCurrentStock())
                .daysOfSupplyRemaining(rs.getDaysOfSupplyRemaining())
                .avgDailyDemand(rs.getAvgDailyDemand())
                .leadTimeDays(rs.getLeadTimeDays())
                .confidenceScore(rs.getConfidenceScore())
                .dataPhase(rs.getDataPhase())
                .status(rs.getStatus())
                .modelVersion(rs.getModelVersion())
                .convertedPrId(rs.getConvertedPrId())
                .computedAt(rs.getComputedAt())
                .createdAt(rs.getCreatedAt())
                .updatedAt(rs.getUpdatedAt())
                .build();
    }
}
