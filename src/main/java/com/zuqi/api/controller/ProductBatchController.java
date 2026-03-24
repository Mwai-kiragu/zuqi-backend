package com.zuqi.api.controller;

import com.zuqi.api.dto.ApiResponse;
import com.zuqi.api.dto.inventory.ProductBatchRequest;
import com.zuqi.api.dto.inventory.ProductBatchResponse;
import com.zuqi.service.ProductBatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/inventory/batches")
@RequiredArgsConstructor
@Tag(name = "Product Batches", description = "Batch and expiry tracking")
public class ProductBatchController {

    private final ProductBatchService productBatchService;

    @PostMapping
    @Operation(summary = "Create a new product batch")
    public ResponseEntity<ApiResponse<ProductBatchResponse>> create(
            @Valid @RequestBody ProductBatchRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Batch created", productBatchService.create(request)));
    }

    @GetMapping
    @Operation(summary = "List all batches")
    public ResponseEntity<ApiResponse<Page<ProductBatchResponse>>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success("Batches",
                productBatchService.getAll(PageRequest.of(page, size, Sort.by("createdAt").descending()))));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a batch by ID")
    public ResponseEntity<ApiResponse<ProductBatchResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Batch", productBatchService.getById(id)));
    }

    @GetMapping("/expiring-soon")
    @Operation(summary = "Get batches expiring within N days (default 30)")
    public ResponseEntity<ApiResponse<List<ProductBatchResponse>>> getExpiringSoon(
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(ApiResponse.success("Expiring batches",
                productBatchService.getExpiringSoon(days)));
    }

    @PatchMapping("/{id}/quantity")
    @Operation(summary = "Update current quantity of a batch")
    public ResponseEntity<ApiResponse<ProductBatchResponse>> updateQuantity(
            @PathVariable UUID id,
            @RequestParam Double quantity) {
        return ResponseEntity.ok(ApiResponse.success("Quantity updated",
                productBatchService.updateQuantity(id, quantity)));
    }
}
