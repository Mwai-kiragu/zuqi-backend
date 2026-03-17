package com.zuqi.api.controller;

import com.zuqi.api.dto.ApiResponse;
import com.zuqi.api.dto.supplier.SupplierBillRequest;
import com.zuqi.api.dto.supplier.SupplierBillResponse;
import com.zuqi.domain.supplier.SupplierBillStatus;
import com.zuqi.service.SupplierBillService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/supplier-bills")
@RequiredArgsConstructor
public class SupplierBillController {

    private final SupplierBillService supplierBillService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<SupplierBillResponse>>> getAll(
            @RequestParam(required = false) SupplierBillStatus status,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(ApiResponse.success(supplierBillService.getAllBills(status, pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SupplierBillResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(supplierBillService.getBillById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SupplierBillResponse>> create(
            @Valid @RequestBody SupplierBillRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(supplierBillService.createBill(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<SupplierBillResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody SupplierBillRequest request) {
        return ResponseEntity.ok(ApiResponse.success(supplierBillService.updateBill(id, request)));
    }

    @PostMapping("/{id}/receive")
    public ResponseEntity<ApiResponse<SupplierBillResponse>> receive(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(supplierBillService.receiveBill(id)));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<SupplierBillResponse>> cancel(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(supplierBillService.cancelBill(id)));
    }

    @GetMapping("/supplier/{supplierId}")
    public ResponseEntity<ApiResponse<Page<SupplierBillResponse>>> getBySupplier(
            @PathVariable UUID supplierId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(ApiResponse.success(supplierBillService.getSupplierBills(supplierId, pageable)));
    }

    @GetMapping("/supplier/{supplierId}/outstanding")
    public ResponseEntity<ApiResponse<List<SupplierBillResponse>>> getOutstanding(
            @PathVariable UUID supplierId) {
        return ResponseEntity.ok(ApiResponse.success(supplierBillService.getOutstandingBillsForSupplier(supplierId)));
    }
}
