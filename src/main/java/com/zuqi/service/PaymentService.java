package com.zuqi.service;

import com.zuqi.api.dto.payment.*;
import com.zuqi.domain.payment.PaymentStatus;
import com.zuqi.domain.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Service interface for Payment operations.
 */
public interface PaymentService {

    /**
     * Get all payments with pagination.
     */
    Page<PaymentResponse> getAllPayments(Pageable pageable);

    /**
     * Get payments by distributor ID with pagination.
     */
    Page<PaymentResponse> getPaymentsByDistributor(UUID distributorId, Pageable pageable);

    /**
     * Get payments by merchant ID with pagination.
     */
    Page<PaymentResponse> getPaymentsByMerchant(UUID merchantId, Pageable pageable);

    /**
     * Get payments by order ID with pagination.
     */
    Page<PaymentResponse> getPaymentsByOrder(UUID orderId, Pageable pageable);

    /**
     * Get payments with filters.
     */
    Page<PaymentResponse> getPaymentsByFilters(
            UUID distributorId,
            PaymentStatus status,
            UUID merchantId,
            Boolean reconciled,
            LocalDate startDate,
            LocalDate endDate,
            Pageable pageable);

    /**
     * Search payments.
     */
    Page<PaymentResponse> searchPayments(UUID distributorId, String search, Pageable pageable);

    /**
     * Get payment by ID.
     */
    PaymentResponse getPaymentById(UUID id);

    /**
     * Get payment by payment number.
     */
    PaymentResponse getPaymentByNumber(String paymentNumber);

    /**
     * Create a new payment.
     */
    PaymentResponse createPayment(PaymentRequest request);

    /**
     * Update payment status.
     */
    PaymentResponse updatePaymentStatus(UUID id, PaymentStatus status);

    /**
     * Reconcile a payment.
     */
    PaymentResponse reconcilePayment(UUID id, ReconcileRequest request, User currentUser);

    /**
     * Get unreconciled payments.
     */
    List<PaymentResponse> getUnreconciledPayments(UUID distributorId);

    /**
     * Count unreconciled payments.
     */
    long countUnreconciledPayments(UUID distributorId);

    /**
     * Get all payment methods.
     */
    List<PaymentMethodResponse> getAllPaymentMethods();

    /**
     * Get active payment methods.
     */
    List<PaymentMethodResponse> getActivePaymentMethods();
}
