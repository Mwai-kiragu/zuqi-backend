package com.zuqi.repository;

import com.zuqi.domain.payment.Payment;
import com.zuqi.domain.payment.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByPaymentNumber(String paymentNumber);

    Page<Payment> findByDistributorId(UUID distributorId, Pageable pageable);

    Page<Payment> findByMerchantId(UUID merchantId, Pageable pageable);

    Page<Payment> findByOrderId(UUID orderId, Pageable pageable);

    Page<Payment> findByStatus(PaymentStatus status, Pageable pageable);

    Page<Payment> findByReconciled(boolean reconciled, Pageable pageable);

    @Query("SELECT p FROM Payment p WHERE p.distributor.id = :distributorId " +
            "AND (:status IS NULL OR p.status = :status) " +
            "AND (:merchantId IS NULL OR p.merchant.id = :merchantId) " +
            "AND (:reconciled IS NULL OR p.reconciled = :reconciled) " +
            "AND (:startDate IS NULL OR p.createdAt >= :startDate) " +
            "AND (:endDate IS NULL OR p.createdAt <= :endDate)")
    Page<Payment> findByFilters(
            @Param("distributorId") UUID distributorId,
            @Param("status") PaymentStatus status,
            @Param("merchantId") UUID merchantId,
            @Param("reconciled") Boolean reconciled,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);

    @Query("SELECT p FROM Payment p WHERE p.distributor.id = :distributorId " +
            "AND (p.paymentNumber LIKE %:search% OR p.externalReference LIKE %:search% " +
            "OR p.merchant.businessName LIKE %:search%)")
    Page<Payment> searchPayments(
            @Param("distributorId") UUID distributorId,
            @Param("search") String search,
            Pageable pageable);

    @Query("SELECT SUM(p.amount) FROM Payment p WHERE p.order.id = :orderId AND p.status = 'COMPLETED'")
    java.math.BigDecimal sumCompletedPaymentsByOrder(@Param("orderId") UUID orderId);

    @Query("SELECT p FROM Payment p WHERE p.distributor.id = :distributorId AND p.reconciled = false")
    List<Payment> findUnreconciledPayments(@Param("distributorId") UUID distributorId);

    @Query("SELECT COUNT(p) FROM Payment p WHERE p.distributor.id = :distributorId AND p.reconciled = false")
    long countUnreconciledPayments(@Param("distributorId") UUID distributorId);

    @Query(value = "SELECT COALESCE(MAX(CAST(SUBSTRING(payment_number, LENGTH(:prefix) + 1) AS INTEGER)), 0) " +
            "FROM payments WHERE payment_number LIKE :prefix%", nativeQuery = true)
    Integer findMaxPaymentNumberByPrefix(@Param("prefix") String prefix);

    // Global queries for SUPER_ADMIN/ADMIN (no distributor filter)
    @Query("SELECT COUNT(p) FROM Payment p WHERE p.reconciled = false")
    long countAllUnreconciledPayments();

    // AI Feature Engineering - Historical queries
    @Query("SELECT p FROM Payment p WHERE p.merchant.id = :merchantId AND p.createdAt < :asOfDate")
    List<Payment> findByMerchantIdAndCreatedAtBefore(
            @Param("merchantId") UUID merchantId,
            @Param("asOfDate") LocalDateTime asOfDate);
}
