package com.zuqi.service;

import com.zuqi.api.dto.inventory.*;
import com.zuqi.domain.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface InventoryService {

    // Stock operations
    Page<StockResponse> getStock(UUID distributorId, UUID warehouseId, UUID branchId, String search, Pageable pageable);

    Page<StockResponse> getStockByWarehouse(UUID warehouseId, Pageable pageable);

    StockResponse getStockByWarehouseAndProduct(UUID warehouseId, UUID productId);

    List<StockResponse> getStockByProduct(UUID productId);

    StockResponse adjustStock(StockAdjustmentRequest request, UUID userId);

    StockMovementResponse approveStockAdjustment(UUID movementId, UUID approverId);

    Page<StockResponse> getLowStock(UUID distributorId, Pageable pageable);

    // Warehouse operations
    List<WarehouseResponse> getWarehousesByDistributor(UUID distributorId);

    List<WarehouseResponse> getWarehousesByBranch(UUID branchId);

    Page<WarehouseResponse> getWarehousesByDistributor(UUID distributorId, Pageable pageable);

    Page<WarehouseResponse> getInactiveWarehousesByDistributor(UUID distributorId, Pageable pageable);

    WarehouseResponse getWarehouseById(UUID warehouseId);

    WarehouseResponse createWarehouse(WarehouseRequest request);

    WarehouseResponse updateWarehouse(UUID warehouseId, WarehouseRequest request);

    void deactivateWarehouse(UUID warehouseId, String reason, User currentUser);

    void activateWarehouse(UUID warehouseId);

    // Stock movement operations
    Page<StockMovementResponse> getMovementsByWarehouse(UUID warehouseId, Pageable pageable);

    Page<StockMovementResponse> getMovementsByWarehouseAndProduct(UUID warehouseId, UUID productId, Pageable pageable);

    Page<StockMovementResponse> getMovementsByDateRange(UUID warehouseId, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);

    Page<StockMovementResponse> getAllMovementsForDistributor(LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);
}
