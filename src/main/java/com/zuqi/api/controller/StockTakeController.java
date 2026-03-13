package com.zuqi.api.controller;

import com.zuqi.api.dto.ApiResponse;
import com.zuqi.api.dto.inventory.StockTakeItemUpdate;
import com.zuqi.api.dto.inventory.StockTakeRequest;
import com.zuqi.api.dto.inventory.StockTakeResponse;
import com.zuqi.service.StockTakeService;
import com.zuqi.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/inventory/stock-takes")
@RequiredArgsConstructor
@Tag(name = "Stock Takes", description = "Physical inventory count (stock take) endpoints")
public class StockTakeController {

    private final StockTakeService stockTakeService;
    private final SecurityUtils securityUtils;

    @PostMapping
    @Operation(summary = "Create a stock take batch")
    public ResponseEntity<ApiResponse<StockTakeResponse>> createStockTake(
            @Valid @RequestBody StockTakeRequest request) {
        UUID userId = securityUtils.getCurrentUserId();
        StockTakeResponse response = stockTakeService.createStockTake(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Stock take created", response));
    }

    @GetMapping
    @Operation(summary = "List stock takes by warehouse")
    public ResponseEntity<ApiResponse<Page<StockTakeResponse>>> getStockTakes(
            @RequestParam(required = false) UUID warehouseId, Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(stockTakeService.getStockTakesByWarehouse(warehouseId, pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get stock take details with items")
    public ResponseEntity<ApiResponse<StockTakeResponse>> getStockTake(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(stockTakeService.getStockTakeById(id)));
    }

    @PutMapping("/{id}/items/{productId}")
    @Operation(summary = "Update counted quantity for a product")
    public ResponseEntity<ApiResponse<StockTakeResponse>> updateItem(
            @PathVariable UUID id,
            @PathVariable UUID productId,
            @Valid @RequestBody StockTakeItemUpdate update) {
        return ResponseEntity.ok(ApiResponse.success("Item updated", stockTakeService.updateItemCount(id, productId, update)));
    }

    @PostMapping("/{id}/complete")
    @Operation(summary = "Mark stock take as complete")
    public ResponseEntity<ApiResponse<StockTakeResponse>> completeStockTake(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Stock take completed", stockTakeService.completeStockTake(id)));
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve stock take and post variances to stock")
    public ResponseEntity<ApiResponse<StockTakeResponse>> approveStockTake(@PathVariable UUID id) {
        UUID userId = securityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success("Stock take approved", stockTakeService.approveStockTake(id, userId)));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel a stock take batch")
    public ResponseEntity<ApiResponse<StockTakeResponse>> cancelStockTake(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Stock take cancelled", stockTakeService.cancelStockTake(id)));
    }
}
