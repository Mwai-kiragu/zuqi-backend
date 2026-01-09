package com.zuqi.api.controller;

import com.zuqi.api.dto.ApiResponse;
import com.zuqi.api.dto.payment.*;
import com.zuqi.domain.payment.PaymentStatus;
import com.zuqi.domain.user.User;
import com.zuqi.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for payment operations.
 */
@RestController
@RequestMapping("/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Payment management APIs")
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * Get all payments with pagination and optional filters.
     */
    @GetMapping
    @Operation(summary = "Get all payments", description = "Retrieves payments with pagination and optional filters")
    public ResponseEntity<ApiResponse<Page<PaymentResponse>>> getAllPayments(
            @Parameter(description = "Distributor ID filter") @RequestParam(required = false) UUID distributorId,
            @Parameter(description = "Merchant ID filter") @RequestParam(required = false) UUID merchantId,
            @Parameter(description = "Order ID filter") @RequestParam(required = false) UUID orderId,
            @Parameter(description = "Payment status filter") @RequestParam(required = false) PaymentStatus status,
            @Parameter(description = "Reconciled filter") @RequestParam(required = false) Boolean reconciled,
            @Parameter(description = "Start date filter") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "End date filter") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @Parameter(description = "Search term") @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<PaymentResponse> payments;

        if (search != null && !search.isBlank() && distributorId != null) {
            payments = paymentService.searchPayments(distributorId, search, pageable);
        } else if (orderId != null) {
            payments = paymentService.getPaymentsByOrder(orderId, pageable);
        } else if (distributorId != null) {
            payments = paymentService.getPaymentsByFilters(
                    distributorId, status, merchantId, reconciled, startDate, endDate, pageable);
        } else if (merchantId != null) {
            payments = paymentService.getPaymentsByMerchant(merchantId, pageable);
        } else {
            payments = paymentService.getAllPayments(pageable);
        }

        return ResponseEntity.ok(ApiResponse.success(payments));
    }

    /**
     * Get a payment by ID.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get payment by ID", description = "Retrieves a specific payment by ID")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPaymentById(
            @Parameter(description = "Payment ID") @PathVariable UUID id) {
        PaymentResponse payment = paymentService.getPaymentById(id);
        return ResponseEntity.ok(ApiResponse.success(payment));
    }

    /**
     * Get a payment by payment number.
     */
    @GetMapping("/number/{paymentNumber}")
    @Operation(summary = "Get payment by number", description = "Retrieves a specific payment by payment number")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPaymentByNumber(
            @Parameter(description = "Payment number") @PathVariable String paymentNumber) {
        PaymentResponse payment = paymentService.getPaymentByNumber(paymentNumber);
        return ResponseEntity.ok(ApiResponse.success(payment));
    }

    /**
     * Create a new payment.
     */
    @PostMapping
    @Operation(summary = "Create payment", description = "Records a new payment")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'DISTRIBUTOR_ADMIN', 'FINANCE', 'MERCHANT')")
    public ResponseEntity<ApiResponse<PaymentResponse>> createPayment(
            @Valid @RequestBody PaymentRequest request) {
        PaymentResponse payment = paymentService.createPayment(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Payment recorded successfully", payment));
    }

    /**
     * Update payment status.
     */
    @PatchMapping("/{id}/status")
    @Operation(summary = "Update payment status", description = "Updates the status of a payment")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'DISTRIBUTOR_ADMIN', 'FINANCE')")
    public ResponseEntity<ApiResponse<PaymentResponse>> updatePaymentStatus(
            @Parameter(description = "Payment ID") @PathVariable UUID id,
            @Parameter(description = "New status") @RequestParam PaymentStatus status) {
        PaymentResponse payment = paymentService.updatePaymentStatus(id, status);
        return ResponseEntity.ok(ApiResponse.success("Payment status updated successfully", payment));
    }

    /**
     * Reconcile a payment.
     */
    @PostMapping("/{id}/reconcile")
    @Operation(summary = "Reconcile payment", description = "Marks a payment as reconciled")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'FINANCE')")
    public ResponseEntity<ApiResponse<PaymentResponse>> reconcilePayment(
            @Parameter(description = "Payment ID") @PathVariable UUID id,
            @RequestBody(required = false) ReconcileRequest request,
            @AuthenticationPrincipal User currentUser) {
        PaymentResponse payment = paymentService.reconcilePayment(id,
                request != null ? request : new ReconcileRequest(), currentUser);
        return ResponseEntity.ok(ApiResponse.success("Payment reconciled successfully", payment));
    }

    /**
     * Get unreconciled payments.
     */
    @GetMapping("/unreconciled")
    @Operation(summary = "Get unreconciled payments", description = "Gets all unreconciled payments")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'DISTRIBUTOR_ADMIN', 'FINANCE')")
    public ResponseEntity<ApiResponse<List<PaymentResponse>>> getUnreconciledPayments(
            @Parameter(description = "Distributor ID") @RequestParam UUID distributorId) {
        List<PaymentResponse> payments = paymentService.getUnreconciledPayments(distributorId);
        return ResponseEntity.ok(ApiResponse.success(payments));
    }

    /**
     * Count unreconciled payments.
     */
    @GetMapping("/unreconciled/count")
    @Operation(summary = "Count unreconciled payments", description = "Gets the count of unreconciled payments")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'DISTRIBUTOR_ADMIN', 'FINANCE')")
    public ResponseEntity<ApiResponse<Long>> countUnreconciledPayments(
            @Parameter(description = "Distributor ID") @RequestParam UUID distributorId) {
        long count = paymentService.countUnreconciledPayments(distributorId);
        return ResponseEntity.ok(ApiResponse.success(count));
    }

    /**
     * Get all payment methods.
     */
    @GetMapping("/methods")
    @Operation(summary = "Get payment methods", description = "Gets all payment methods")
    public ResponseEntity<ApiResponse<List<PaymentMethodResponse>>> getPaymentMethods() {
        List<PaymentMethodResponse> methods = paymentService.getActivePaymentMethods();
        return ResponseEntity.ok(ApiResponse.success(methods));
    }
}
