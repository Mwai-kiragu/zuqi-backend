package com.zuqi.api.controller;

import com.zuqi.api.dto.ApiResponse;
import com.zuqi.api.dto.merchant.MerchantRequest;
import com.zuqi.api.dto.merchant.MerchantResponse;
import com.zuqi.service.MerchantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/merchants")
@RequiredArgsConstructor
@Tag(name = "Merchants", description = "Merchant brand management APIs")
public class MerchantController {

    private final MerchantService merchantService;

    @GetMapping
    @Operation(summary = "Get all merchant brands")
    public ResponseEntity<ApiResponse<Page<MerchantResponse>>> getAllMerchants(
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(merchantService.getAllMerchants(active, search, pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<MerchantResponse>> getMerchantById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(merchantService.getMerchantById(id)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<MerchantResponse>> createMerchant(@Valid @RequestBody MerchantRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Merchant brand created successfully", merchantService.createMerchant(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<MerchantResponse>> updateMerchant(@PathVariable UUID id,
            @Valid @RequestBody MerchantRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Merchant brand updated successfully",
                merchantService.updateMerchant(id, request)));
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deactivateMerchant(@PathVariable UUID id) {
        merchantService.deactivateMerchant(id);
        return ResponseEntity.ok(ApiResponse.success("Merchant brand deactivated successfully"));
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> activateMerchant(@PathVariable UUID id) {
        merchantService.activateMerchant(id);
        return ResponseEntity.ok(ApiResponse.success("Merchant brand activated successfully"));
    }
}
