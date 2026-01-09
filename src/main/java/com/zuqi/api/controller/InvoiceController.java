package com.zuqi.api.controller;

import com.zuqi.api.dto.ApiResponse;
import com.zuqi.api.dto.invoice.InvoiceResponse;
import com.zuqi.api.dto.invoice.SendInvoiceRequest;
import com.zuqi.domain.invoice.InvoiceStatus;
import com.zuqi.service.InvoiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/invoices")
@RequiredArgsConstructor
@Tag(name = "Invoices", description = "Invoice management APIs")
public class InvoiceController {

    private final InvoiceService invoiceService;

    @GetMapping
    @Operation(summary = "Get all invoices with optional filters")
    public ResponseEntity<ApiResponse<Page<InvoiceResponse>>> getAllInvoices(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) UUID distributorId,
            @RequestParam(required = false) InvoiceStatus status,
            @RequestParam(required = false) UUID merchantId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String search) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Page<InvoiceResponse> invoices;
        if (search != null && !search.isEmpty()) {
            invoices = invoiceService.searchInvoices(distributorId, search, pageable);
        } else if (status != null || merchantId != null || startDate != null || endDate != null) {
            invoices = invoiceService.getInvoicesByFilters(distributorId, status, merchantId, startDate, endDate, pageable);
        } else {
            invoices = invoiceService.getAllInvoices(pageable);
        }

        return ResponseEntity.ok(ApiResponse.success("Invoices retrieved successfully", invoices));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get invoice by ID")
    public ResponseEntity<ApiResponse<InvoiceResponse>> getInvoiceById(@PathVariable UUID id) {
        InvoiceResponse invoice = invoiceService.getInvoiceById(id);
        return ResponseEntity.ok(ApiResponse.success("Invoice retrieved successfully", invoice));
    }

    @GetMapping("/number/{invoiceNumber}")
    @Operation(summary = "Get invoice by invoice number")
    public ResponseEntity<ApiResponse<InvoiceResponse>> getInvoiceByNumber(@PathVariable String invoiceNumber) {
        InvoiceResponse invoice = invoiceService.getInvoiceByNumber(invoiceNumber);
        return ResponseEntity.ok(ApiResponse.success("Invoice retrieved successfully", invoice));
    }

    @GetMapping("/order/{orderId}")
    @Operation(summary = "Get invoice by order ID")
    public ResponseEntity<ApiResponse<InvoiceResponse>> getInvoiceByOrderId(@PathVariable UUID orderId) {
        InvoiceResponse invoice = invoiceService.getInvoiceByOrderId(orderId);
        return ResponseEntity.ok(ApiResponse.success("Invoice retrieved successfully", invoice));
    }

    @GetMapping("/distributor/{distributorId}")
    @Operation(summary = "Get invoices by distributor")
    public ResponseEntity<ApiResponse<Page<InvoiceResponse>>> getInvoicesByDistributor(
            @PathVariable UUID distributorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<InvoiceResponse> invoices = invoiceService.getInvoicesByDistributor(distributorId, pageable);

        return ResponseEntity.ok(ApiResponse.success("Invoices retrieved successfully", invoices));
    }

    @GetMapping("/merchant/{merchantId}")
    @Operation(summary = "Get invoices by merchant")
    public ResponseEntity<ApiResponse<Page<InvoiceResponse>>> getInvoicesByMerchant(
            @PathVariable UUID merchantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<InvoiceResponse> invoices = invoiceService.getInvoicesByMerchant(merchantId, pageable);

        return ResponseEntity.ok(ApiResponse.success("Invoices retrieved successfully", invoices));
    }

    @PostMapping("/{id}/send")
    @Operation(summary = "Send invoice via email")
    public ResponseEntity<ApiResponse<InvoiceResponse>> sendInvoice(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) SendInvoiceRequest request) {

        String email = request != null ? request.getEmail() : null;
        InvoiceResponse invoice = invoiceService.sendInvoice(id, email);

        return ResponseEntity.ok(ApiResponse.success("Invoice sent successfully", invoice));
    }

    @PostMapping("/{id}/viewed")
    @Operation(summary = "Mark invoice as viewed")
    public ResponseEntity<ApiResponse<InvoiceResponse>> markAsViewed(@PathVariable UUID id) {
        InvoiceResponse invoice = invoiceService.markAsViewed(id);
        return ResponseEntity.ok(ApiResponse.success("Invoice marked as viewed", invoice));
    }

    @PostMapping("/{id}/payment")
    @Operation(summary = "Record payment against invoice")
    public ResponseEntity<ApiResponse<InvoiceResponse>> recordPayment(
            @PathVariable UUID id,
            @RequestParam BigDecimal amount) {

        InvoiceResponse invoice = invoiceService.recordPayment(id, amount);
        return ResponseEntity.ok(ApiResponse.success("Payment recorded successfully", invoice));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel invoice")
    public ResponseEntity<ApiResponse<InvoiceResponse>> cancelInvoice(@PathVariable UUID id) {
        InvoiceResponse invoice = invoiceService.cancelInvoice(id);
        return ResponseEntity.ok(ApiResponse.success("Invoice cancelled successfully", invoice));
    }

    @GetMapping("/overdue")
    @Operation(summary = "Get overdue invoices")
    public ResponseEntity<ApiResponse<List<InvoiceResponse>>> getOverdueInvoices() {
        List<InvoiceResponse> invoices = invoiceService.getOverdueInvoices();
        return ResponseEntity.ok(ApiResponse.success("Overdue invoices retrieved successfully", invoices));
    }

    @GetMapping("/count")
    @Operation(summary = "Get invoice count by status")
    public ResponseEntity<ApiResponse<Long>> getInvoiceCountByStatus(
            @RequestParam(required = false) UUID distributorId,
            @RequestParam InvoiceStatus status) {

        long count = invoiceService.getInvoiceCountByStatus(distributorId, status);
        return ResponseEntity.ok(ApiResponse.success("Invoice count retrieved successfully", count));
    }

    @GetMapping("/status-counts")
    @Operation(summary = "Get invoice counts for all statuses in one call")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getAllStatusCounts(
            @RequestParam(required = false) UUID distributorId) {

        Map<String, Long> counts = invoiceService.getAllStatusCounts(distributorId);
        return ResponseEntity.ok(ApiResponse.success("Invoice status counts retrieved successfully", counts));
    }
}
