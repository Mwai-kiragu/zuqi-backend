package com.zuqi.api.dto.inventory;

import com.zuqi.domain.inventory.StockTakeBatchStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class StockTakeResponse {

    private UUID id;
    private String referenceNumber;
    private UUID warehouseId;
    private String warehouseName;
    private UUID branchId;
    private String branchName;
    private StockTakeBatchStatus status;
    private String notes;
    private List<StockTakeItemResponse> items;
    private UUID createdById;
    private String createdByName;
    private UUID approvedById;
    private String approvedByName;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime approvedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
