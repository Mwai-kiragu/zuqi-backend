package com.zuqi.api.controller;

import com.zuqi.api.dto.ApiResponse;
import com.zuqi.api.dto.returns.CreatePurchaseReturnRequest;
import com.zuqi.api.dto.returns.PurchaseReturnResponse;
import com.zuqi.domain.user.User;
import com.zuqi.service.PurchaseReturnService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/purchase-returns")
@RequiredArgsConstructor
@Tag(name = "Purchase Returns", description = "Supplier purchase returns management")
public class PurchaseReturnController {

    private final PurchaseReturnService purchaseReturnService;

    @PostMapping
    public ResponseEntity<ApiResponse<PurchaseReturnResponse>> create(
            @Valid @RequestBody CreatePurchaseReturnRequest request,
            @AuthenticationPrincipal User currentUser) {
        PurchaseReturnResponse response = purchaseReturnService.create(request, currentUser.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Purchase return created", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<PurchaseReturnResponse>>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<PurchaseReturnResponse> result = purchaseReturnService.getAll(
                PageRequest.of(page, size, Sort.by("createdAt").descending()));
        return ResponseEntity.ok(ApiResponse.success("Purchase returns", result));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PurchaseReturnResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Purchase return", purchaseReturnService.getById(id)));
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<ApiResponse<PurchaseReturnResponse>> confirm(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Confirmed", purchaseReturnService.confirm(id)));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<PurchaseReturnResponse>> cancel(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Cancelled", purchaseReturnService.cancel(id)));
    }
}
