package com.zuqi.api.controller;

import com.zuqi.api.dto.ApiResponse;
import com.zuqi.api.dto.common.DeactivateRequest;
import com.zuqi.api.dto.inventory.*;
import com.zuqi.domain.user.User;
import com.zuqi.service.InventoryService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;


@RestController
@RequestMapping("/v1/inventory")
@RequiredArgsConstructor
@Tag(name = "Inventory", description = "Inventory management APIs")
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping
    @Operation(summary = "Get stock with optional filters", description = "Get all stock, optionally filtered by distributor and/or warehouse")
    public ResponseEntity<ApiResponse<Page<StockResponse>>> getStock(
            @Parameter(description = "Distributor ID (optional)") @RequestParam(required = false) UUID distributorId,
            @Parameter(description = "Warehouse ID (optional)") @RequestParam(required = false) UUID warehouseId,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<StockResponse> stock = inventoryService.getStock(distributorId, warehouseId, pageable);
        return ResponseEntity.ok(ApiResponse.success(stock));
    }

    @GetMapping("/warehouse/{warehouseId}")
    @Operation(summary = "Get stock by warehouse", description = "Get all stock levels for a warehouse")
    public ResponseEntity<ApiResponse<Page<StockResponse>>> getStockByWarehouse(
            @PathVariable UUID warehouseId,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<StockResponse> stock = inventoryService.getStockByWarehouse(warehouseId, pageable);
        return ResponseEntity.ok(ApiResponse.success(stock));
    }

    @GetMapping("/warehouse/{warehouseId}/product/{productId}")
    @Operation(summary = "Get stock for specific product", description = "Get stock level for a specific product in a warehouse")
    public ResponseEntity<ApiResponse<StockResponse>> getStockByWarehouseAndProduct(
            @PathVariable UUID warehouseId,
            @PathVariable UUID productId) {
        StockResponse stock = inventoryService.getStockByWarehouseAndProduct(warehouseId, productId);
        return ResponseEntity.ok(ApiResponse.success(stock));
    }

    @GetMapping("/product/{productId}")
    @Operation(summary = "Get stock across warehouses", description = "Get stock levels for a product across all warehouses")
    public ResponseEntity<ApiResponse<List<StockResponse>>> getStockByProduct(
            @PathVariable UUID productId) {
        List<StockResponse> stock = inventoryService.getStockByProduct(productId);
        return ResponseEntity.ok(ApiResponse.success(stock));
    }

    @PostMapping("/adjust")
    @Operation(summary = "Adjust stock", description = "Adjust stock levels (IN, OUT, ADJUSTMENT, TRANSFER). INITIATOR role creates a pending movement awaiting approval.")
    public ResponseEntity<ApiResponse<StockResponse>> adjustStock(
            @Valid @RequestBody StockAdjustmentRequest request,
            @AuthenticationPrincipal User currentUser) {
        StockResponse stock = inventoryService.adjustStock(request, currentUser.getId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Stock adjusted successfully", stock));
    }

    @PostMapping("/movements/{movementId}/approve")
    @Operation(summary = "Approve a pending stock adjustment", description = "VERIFIER/AUTHORIZER approves a pending stock movement and applies the quantity change")
    public ResponseEntity<ApiResponse<StockMovementResponse>> approveStockAdjustment(
            @PathVariable UUID movementId,
            @AuthenticationPrincipal User currentUser) {
        StockMovementResponse response = inventoryService.approveStockAdjustment(movementId, currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success("Stock adjustment approved", response));
    }

    @GetMapping("/low-stock")
    @Operation(summary = "Get low stock items", description = "Get items below reorder level for a distributor")
    public ResponseEntity<ApiResponse<Page<StockResponse>>> getLowStock(
            @Parameter(description = "Distributor ID") @RequestParam UUID distributorId,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<StockResponse> lowStock = inventoryService.getLowStock(distributorId, pageable);
        return ResponseEntity.ok(ApiResponse.success(lowStock));
    }

    @GetMapping("/warehouses")
    @Operation(summary = "List warehouses", description = "Get all active warehouses for a distributor or branch")
    public ResponseEntity<ApiResponse<List<WarehouseResponse>>> getWarehouses(
            @Parameter(description = "Distributor ID (optional for admins)") @RequestParam(required = false) UUID distributorId,
            @Parameter(description = "Branch ID filter") @RequestParam(required = false) UUID branchId) {
        List<WarehouseResponse> warehouses = branchId != null
                ? inventoryService.getWarehousesByBranch(branchId)
                : inventoryService.getWarehousesByDistributor(distributorId);
        return ResponseEntity.ok(ApiResponse.success(warehouses));
    }

    @GetMapping("/warehouses/page")
    @Operation(summary = "List warehouses (paginated)", description = "Get warehouses with pagination")
    public ResponseEntity<ApiResponse<Page<WarehouseResponse>>> getWarehousesPaged(
            @Parameter(description = "Distributor ID (optional for admins)") @RequestParam(required = false) UUID distributorId,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<WarehouseResponse> warehouses = inventoryService.getWarehousesByDistributor(distributorId, pageable);
        return ResponseEntity.ok(ApiResponse.success(warehouses));
    }

    @GetMapping("/warehouses/{warehouseId}")
    @Operation(summary = "Get warehouse by ID", description = "Get warehouse details")
    public ResponseEntity<ApiResponse<WarehouseResponse>> getWarehouseById(
            @PathVariable UUID warehouseId) {
        WarehouseResponse warehouse = inventoryService.getWarehouseById(warehouseId);
        return ResponseEntity.ok(ApiResponse.success(warehouse));
    }

    @PostMapping("/warehouses")
    @Operation(summary = "Create warehouse", description = "Create a new warehouse")
    public ResponseEntity<ApiResponse<WarehouseResponse>> createWarehouse(
            @Valid @RequestBody WarehouseRequest request) {
        WarehouseResponse warehouse = inventoryService.createWarehouse(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Warehouse created successfully", warehouse));
    }

    @PutMapping("/warehouses/{warehouseId}")
    @Operation(summary = "Update warehouse", description = "Update warehouse details")
    public ResponseEntity<ApiResponse<WarehouseResponse>> updateWarehouse(
            @PathVariable UUID warehouseId,
            @Valid @RequestBody WarehouseRequest request) {
        WarehouseResponse warehouse = inventoryService.updateWarehouse(warehouseId, request);
        return ResponseEntity.ok(ApiResponse.success("Warehouse updated successfully", warehouse));
    }

    @DeleteMapping("/warehouses/{warehouseId}")
    @Operation(summary = "Deactivate warehouse", description = "Deactivate a warehouse with reason")
    public ResponseEntity<ApiResponse<Void>> deactivateWarehouse(
            @PathVariable UUID warehouseId,
            @Valid @RequestBody DeactivateRequest request,
            @AuthenticationPrincipal User currentUser) {
        inventoryService.deactivateWarehouse(warehouseId, request.getReason(), currentUser);
        return ResponseEntity.ok(ApiResponse.success("Warehouse deactivated successfully", null));
    }

    @PostMapping("/warehouses/{warehouseId}/activate")
    @Operation(summary = "Activate warehouse", description = "Reactivate a deactivated warehouse")
    public ResponseEntity<ApiResponse<Void>> activateWarehouse(@PathVariable UUID warehouseId) {
        inventoryService.activateWarehouse(warehouseId);
        return ResponseEntity.ok(ApiResponse.success("Warehouse activated successfully", null));
    }

    @GetMapping("/movements/warehouse/{warehouseId}")
    @Operation(summary = "Get stock movements", description = "Get stock movements for a warehouse")
    public ResponseEntity<ApiResponse<Page<StockMovementResponse>>> getMovementsByWarehouse(
            @PathVariable UUID warehouseId,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<StockMovementResponse> movements = inventoryService.getMovementsByWarehouse(warehouseId, pageable);
        return ResponseEntity.ok(ApiResponse.success(movements));
    }

    @GetMapping("/movements/warehouse/{warehouseId}/product/{productId}")
    @Operation(summary = "Get product movements", description = "Get stock movements for a product in a warehouse")
    public ResponseEntity<ApiResponse<Page<StockMovementResponse>>> getMovementsByWarehouseAndProduct(
            @PathVariable UUID warehouseId,
            @PathVariable UUID productId,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<StockMovementResponse> movements = inventoryService.getMovementsByWarehouseAndProduct(warehouseId, productId, pageable);
        return ResponseEntity.ok(ApiResponse.success(movements));
    }

    @GetMapping("/movements/warehouse/{warehouseId}/date-range")
    @Operation(summary = "Get movements by date range", description = "Get stock movements within a date range")
    public ResponseEntity<ApiResponse<Page<StockMovementResponse>>> getMovementsByDateRange(
            @PathVariable UUID warehouseId,
            @Parameter(description = "Start date (ISO format)")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @Parameter(description = "End date (ISO format)")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<StockMovementResponse> movements = inventoryService.getMovementsByDateRange(warehouseId, startDate, endDate, pageable);
        return ResponseEntity.ok(ApiResponse.success(movements));
    }
}
