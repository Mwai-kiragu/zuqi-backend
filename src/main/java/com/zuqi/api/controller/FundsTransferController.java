package com.zuqi.api.controller;

import com.zuqi.api.dto.ApiResponse;
import com.zuqi.api.dto.ft.FtAmountRangeRequest;
import com.zuqi.api.dto.ft.FtAmountRangeResponse;
import com.zuqi.api.dto.ft.FundsTransferRequest;
import com.zuqi.api.dto.ft.FundsTransferResponse;
import com.zuqi.domain.ft.FundsTransferStatus;
import com.zuqi.service.FundsTransferService;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/funds-transfers")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','DISTRIBUTOR_ADMIN','MERCHANT_ADMIN','FINANCE')")
public class FundsTransferController {

    private final FundsTransferService fundsTransferService;
    private final SecurityUtils securityUtils;

    // ── Transfers ─────────────────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<ApiResponse<Page<FundsTransferResponse>>> getAll(
            @RequestParam(required = false) FundsTransferStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(ApiResponse.success(fundsTransferService.getAll(status, startDate, endDate, pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<FundsTransferResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(fundsTransferService.getById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<FundsTransferResponse>> create(@Valid @RequestBody FundsTransferRequest request) {
        UUID distributorId = securityUtils.getDistributorIdForFiltering();
        if (distributorId == null) {
            distributorId = securityUtils.getCurrentUserDistributorId();
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(fundsTransferService.create(distributorId, request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<FundsTransferResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody FundsTransferRequest request) {
        return ResponseEntity.ok(ApiResponse.success(fundsTransferService.update(id, request)));
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<ApiResponse<FundsTransferResponse>> submit(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(fundsTransferService.submit(id)));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<FundsTransferResponse>> approve(
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, String> body) {
        String comment = body != null ? body.get("comment") : null;
        return ResponseEntity.ok(ApiResponse.success(fundsTransferService.approve(id, comment)));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<FundsTransferResponse>> reject(
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        return ResponseEntity.ok(ApiResponse.success(fundsTransferService.reject(id, reason)));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<FundsTransferResponse>> cancel(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(fundsTransferService.cancel(id)));
    }

    @PostMapping("/{id}/disburse")
    public ResponseEntity<ApiResponse<FundsTransferResponse>> disburse(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(fundsTransferService.disburse(id)));
    }

    // ── Amount Range Configuration ────────────────────────────────────────────

    @GetMapping("/amount-ranges")
    public ResponseEntity<ApiResponse<List<FtAmountRangeResponse>>> getAmountRanges() {
        UUID distributorId = securityUtils.getDistributorIdForFiltering();
        if (distributorId == null) distributorId = securityUtils.getCurrentUserDistributorId();
        return ResponseEntity.ok(ApiResponse.success(fundsTransferService.getAmountRanges(distributorId)));
    }

    @PostMapping("/amount-ranges")
    public ResponseEntity<ApiResponse<FtAmountRangeResponse>> createAmountRange(
            @Valid @RequestBody FtAmountRangeRequest request) {
        UUID distributorId = securityUtils.getDistributorIdForFiltering();
        if (distributorId == null) distributorId = securityUtils.getCurrentUserDistributorId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(fundsTransferService.createAmountRange(distributorId, request)));
    }

    @PutMapping("/amount-ranges/{rangeId}")
    public ResponseEntity<ApiResponse<FtAmountRangeResponse>> updateAmountRange(
            @PathVariable UUID rangeId,
            @Valid @RequestBody FtAmountRangeRequest request) {
        return ResponseEntity.ok(ApiResponse.success(fundsTransferService.updateAmountRange(rangeId, request)));
    }

    @DeleteMapping("/amount-ranges/{rangeId}")
    public ResponseEntity<ApiResponse<Void>> deleteAmountRange(@PathVariable UUID rangeId) {
        fundsTransferService.deleteAmountRange(rangeId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
