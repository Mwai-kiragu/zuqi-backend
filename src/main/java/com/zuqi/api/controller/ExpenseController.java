package com.zuqi.api.controller;

import com.zuqi.api.dto.expense.ExpenseRequest;
import com.zuqi.api.dto.expense.ExpenseResponse;
import com.zuqi.api.dto.ApiResponse;
import com.zuqi.domain.expense.ExpenseStatus;
import com.zuqi.service.ExpenseService;
import com.zuqi.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/expenses")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','DISTRIBUTOR_ADMIN','MERCHANT_ADMIN','FINANCE')")
public class ExpenseController {

    private final ExpenseService expenseService;
    private final SecurityUtils securityUtils;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<ExpenseResponse>>> getAll(
            @RequestParam(required = false) ExpenseStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(ApiResponse.success(expenseService.getAll(status, startDate, endDate, pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ExpenseResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(expenseService.getById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ExpenseResponse>> create(@Valid @RequestBody ExpenseRequest request) {
        UUID distributorId = securityUtils.getDistributorIdForFiltering();
        if (distributorId == null) {
            // For MERCHANT_ADMIN, require distributorId from request context — use first distributor
            // For SUPER_ADMIN test scenarios, fall through
            distributorId = securityUtils.getCurrentUserDistributorId();
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(expenseService.create(distributorId, request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ExpenseResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody ExpenseRequest request) {
        return ResponseEntity.ok(ApiResponse.success(expenseService.update(id, request)));
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<ApiResponse<ExpenseResponse>> submit(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(expenseService.submit(id)));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<ExpenseResponse>> approve(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(expenseService.approve(id)));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<ExpenseResponse>> reject(
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        return ResponseEntity.ok(ApiResponse.success(expenseService.reject(id, reason)));
    }

    @PostMapping("/{id}/pay")
    public ResponseEntity<ApiResponse<ExpenseResponse>> markPaid(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(expenseService.markPaid(id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        expenseService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
