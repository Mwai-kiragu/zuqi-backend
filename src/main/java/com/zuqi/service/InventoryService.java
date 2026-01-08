package com.zuqi.service;

import com.zuqi.api.dto.inventory.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service interface for inventory operations.
 */
public interface InventoryService {

    // Stock operations
    Page<StockResponse> getStockByWarehouse(UUID warehouseId, Pageable pageable);

    StockResponse getStockByWarehouseAndProduct(UUID warehouseId, UUID productId);

    List<StockResponse> getStockByProduct(UUID productId);

    StockResponse adjustStock(StockAdjustmentRequest request, UUID userId);

    Page<StockResponse> getLowStock(UUID distributorId, Pageable pageable);

    // Warehouse operations
    List<WarehouseResponse> getWarehousesByDistributor(UUID distributorId);

    Page<WarehouseResponse> getWarehousesByDistributor(UUID distributorId, Pageable pageable);

    WarehouseResponse getWarehouseById(UUID warehouseId);

    WarehouseResponse createWarehouse(WarehouseRequest request);

    WarehouseResponse updateWarehouse(UUID warehouseId, WarehouseRequest request);

    void deactivateWarehouse(UUID warehouseId);

    // Stock movement operations
    Page<StockMovementResponse> getMovementsByWarehouse(UUID warehouseId, Pageable pageable);

    Page<StockMovementResponse> getMovementsByWarehouseAndProduct(UUID warehouseId, UUID productId, Pageable pageable);

    Page<StockMovementResponse> getMovementsByDateRange(UUID warehouseId, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);
}
