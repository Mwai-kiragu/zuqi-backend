package com.zuqi.api.controller;

import com.zuqi.api.dto.crm.CustomerInteractionRequest;
import com.zuqi.api.dto.crm.CustomerInteractionResponse;
import com.zuqi.api.dto.ApiResponse;
import com.zuqi.service.CustomerInteractionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/crm/interactions")
@RequiredArgsConstructor
public class CustomerInteractionController {

    private final CustomerInteractionService interactionService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<CustomerInteractionResponse>>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) UUID customerId) {
        Page<CustomerInteractionResponse> result = customerId != null
                ? interactionService.getByCustomerId(customerId, PageRequest.of(page, size, Sort.by("createdAt").descending()))
                : interactionService.getAll(PageRequest.of(page, size, Sort.by("createdAt").descending()));
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/follow-ups")
    public ResponseEntity<ApiResponse<Page<CustomerInteractionResponse>>> getPendingFollowUps(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                interactionService.getPendingFollowUps(PageRequest.of(page, size))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CustomerInteractionResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(interactionService.getById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CustomerInteractionResponse>> create(
            @Valid @RequestBody CustomerInteractionRequest request) {
        return ResponseEntity.ok(ApiResponse.success(interactionService.create(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CustomerInteractionResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody CustomerInteractionRequest request) {
        return ResponseEntity.ok(ApiResponse.success(interactionService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        interactionService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
