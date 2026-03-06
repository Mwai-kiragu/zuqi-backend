package com.zuqi.api.controller;

import com.zuqi.api.dto.ApiResponse;
import com.zuqi.api.dto.inventory.StockTransferRequest;
import com.zuqi.api.dto.inventory.StockTransferResponse;
import com.zuqi.service.StockTransferService;
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
@RequestMapping("/v1/inventory/transfers")
@RequiredArgsConstructor
@Tag(name = "Stock Transfers", description = "Inter-branch/warehouse stock transfer endpoints")
public class StockTransferController {

    private final StockTransferService stockTransferService;
    private final SecurityUtils securityUtils;

    @PostMapping
    @Operation(summary = "Create a stock transfer request")
    public ResponseEntity<ApiResponse<StockTransferResponse>> createTransfer(
            @Valid @RequestBody StockTransferRequest request) {
        UUID userId = securityUtils.getCurrentUserId();
        StockTransferResponse response = stockTransferService.createTransfer(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Transfer created", response));
    }

    @GetMapping
    @Operation(summary = "List transfers")
    public ResponseEntity<ApiResponse<Page<StockTransferResponse>>> getTransfers(
            @RequestParam(required = false) String status,
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(stockTransferService.getTransfers(status, pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get transfer details")
    public ResponseEntity<ApiResponse<StockTransferResponse>> getTransfer(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(stockTransferService.getTransferById(id)));
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve a transfer")
    public ResponseEntity<ApiResponse<StockTransferResponse>> approveTransfer(@PathVariable UUID id) {
        UUID userId = securityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success("Transfer approved", stockTransferService.approveTransfer(id, userId)));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel a transfer")
    public ResponseEntity<ApiResponse<StockTransferResponse>> cancelTransfer(
            @PathVariable UUID id, @RequestParam(required = false) String reason) {
        return ResponseEntity.ok(ApiResponse.success("Transfer cancelled", stockTransferService.cancelTransfer(id, reason)));
    }

    @PostMapping("/{id}/receive")
    @Operation(summary = "Mark transfer as received (updates stock)")
    public ResponseEntity<ApiResponse<StockTransferResponse>> receiveTransfer(@PathVariable UUID id) {
        UUID userId = securityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success("Transfer received", stockTransferService.receiveTransfer(id, userId)));
    }
}
