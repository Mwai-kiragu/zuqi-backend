package com.zuqi.api.dto.inventory;

import com.zuqi.domain.inventory.StockTransferStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class StockTransferResponse {

    private UUID id;
    private String referenceNumber;
    private UUID sourceWarehouseId;
    private String sourceWarehouseName;
    private UUID destinationWarehouseId;
    private String destinationWarehouseName;
    private UUID sourceBranchId;
    private String sourceBranchName;
    private UUID destinationBranchId;
    private String destinationBranchName;
    private StockTransferStatus status;
    private String notes;
    private List<StockTransferItemResponse> items;
    private UUID requestedById;
    private String requestedByName;
    private UUID approvedById;
    private String approvedByName;
    private LocalDateTime approvedAt;
    private LocalDateTime dispatchedAt;
    private LocalDateTime receivedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
