package com.zuqi.api.controller;

import com.zuqi.api.dto.ApiResponse;
import com.zuqi.api.dto.pricing.CreatePromotionRequest;
import com.zuqi.api.dto.pricing.PromotionResponse;
import com.zuqi.service.PromotionService;
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
@RequestMapping("/v1/promotions")
@RequiredArgsConstructor
@Tag(name = "Promotions", description = "Discount and promotion management")
public class PromotionController {

    private final PromotionService promotionService;

    @PostMapping
    public ResponseEntity<ApiResponse<PromotionResponse>> create(@Valid @RequestBody CreatePromotionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Promotion created", promotionService.create(request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<PromotionResponse>>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success("Promotions",
                promotionService.getAll(PageRequest.of(page, size, Sort.by("createdAt").descending()))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PromotionResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Promotion", promotionService.getById(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PromotionResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody CreatePromotionRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Promotion updated", promotionService.update(id, request)));
    }

    @PostMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<PromotionResponse>> deactivate(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Promotion deactivated", promotionService.deactivate(id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        promotionService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Promotion deleted", null));
    }
}
