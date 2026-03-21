package com.zuqi.api.controller;

import com.zuqi.api.dto.ApiResponse;
import com.zuqi.api.dto.returns.CreateSalesReturnRequest;
import com.zuqi.api.dto.returns.SalesReturnResponse;
import com.zuqi.domain.user.User;
import com.zuqi.service.SalesReturnService;
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
@RequestMapping("/v1/sales-returns")
@RequiredArgsConstructor
@Tag(name = "Sales Returns", description = "Customer sales returns management")
public class SalesReturnController {

    private final SalesReturnService salesReturnService;

    @PostMapping
    public ResponseEntity<ApiResponse<SalesReturnResponse>> create(
            @Valid @RequestBody CreateSalesReturnRequest request,
            @AuthenticationPrincipal User currentUser) {
        SalesReturnResponse response = salesReturnService.create(request, currentUser.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Sales return created", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<SalesReturnResponse>>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<SalesReturnResponse> result = salesReturnService.getAll(
                PageRequest.of(page, size, Sort.by("createdAt").descending()));
        return ResponseEntity.ok(ApiResponse.success("Sales returns", result));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SalesReturnResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Sales return", salesReturnService.getById(id)));
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<ApiResponse<SalesReturnResponse>> confirm(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Confirmed", salesReturnService.confirm(id)));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<SalesReturnResponse>> cancel(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Cancelled", salesReturnService.cancel(id)));
    }
}
