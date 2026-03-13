package com.zuqi.service;

import com.zuqi.api.dto.inventory.StockTakeItemUpdate;
import com.zuqi.api.dto.inventory.StockTakeRequest;
import com.zuqi.api.dto.inventory.StockTakeResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface StockTakeService {

    StockTakeResponse createStockTake(StockTakeRequest request, UUID createdByUserId);

    Page<StockTakeResponse> getStockTakesByWarehouse(UUID warehouseId, Pageable pageable);

    StockTakeResponse getStockTakeById(UUID batchId);

    StockTakeResponse updateItemCount(UUID batchId, UUID productId, StockTakeItemUpdate update);

    StockTakeResponse completeStockTake(UUID batchId);

    StockTakeResponse approveStockTake(UUID batchId, UUID approvedByUserId);

    StockTakeResponse cancelStockTake(UUID batchId);
}
