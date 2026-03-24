package com.zuqi.api.controller;

import com.zuqi.api.dto.ApiResponse;
import com.zuqi.api.dto.pricing.CreatePriceListRequest;
import com.zuqi.api.dto.pricing.PriceListResponse;
import com.zuqi.service.PriceListService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/price-lists")
@RequiredArgsConstructor
@Tag(name = "Price Lists", description = "Customer price list management")
public class PriceListController {

    private final PriceListService priceListService;

    @PostMapping
    public ResponseEntity<ApiResponse<PriceListResponse>> create(@Valid @RequestBody CreatePriceListRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Price list created", priceListService.create(request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<PriceListResponse>>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success("Price lists",
                priceListService.getAll(PageRequest.of(page, size, Sort.by("createdAt").descending()))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PriceListResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Price list", priceListService.getById(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PriceListResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody CreatePriceListRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Price list updated", priceListService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        priceListService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Price list deleted", null));
    }
}
