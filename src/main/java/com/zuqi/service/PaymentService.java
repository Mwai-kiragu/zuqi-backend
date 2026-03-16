package com.zuqi.service;

import com.zuqi.api.dto.payment.*;
import com.zuqi.domain.payment.PaymentStatus;
import com.zuqi.domain.pos.PosSale;
import com.zuqi.domain.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface PaymentService {

    Page<PaymentResponse> getAllPayments(Pageable pageable);

    Page<PaymentResponse> getPaymentsByDistributor(UUID distributorId, Pageable pageable);

    Page<PaymentResponse> getPaymentsByMerchant(UUID merchantId, Pageable pageable);

    Page<PaymentResponse> getPaymentsByOrder(UUID orderId, Pageable pageable);

    Page<PaymentResponse> getPaymentsByFilters(
            UUID distributorId,
            PaymentStatus status,
            UUID merchantId,
            Boolean reconciled,
            Long paymentMethodId,
            LocalDate startDate,
            LocalDate endDate,
            Pageable pageable);

    Page<PaymentResponse> searchPayments(UUID distributorId, String search, Pageable pageable);

    PaymentResponse getPaymentById(UUID id);

    PaymentResponse getPaymentByNumber(String paymentNumber);

    PaymentResponse createPayment(PaymentRequest request);

    PaymentResponse updatePaymentStatus(UUID id, PaymentStatus status);

    PaymentResponse reconcilePayment(UUID id, ReconcileRequest request, User currentUser);

    List<PaymentResponse> getUnreconciledPayments(UUID distributorId);

    long countUnreconciledPayments(UUID distributorId);

    List<PaymentMethodResponse> getAllPaymentMethods();

    List<PaymentMethodResponse> getActivePaymentMethods();

    void createPaymentsForPosSale(PosSale sale);
}
