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

    @Query(value = "SELECT * FROM payments p WHERE p.distributor_id = :distributorId " +
            "AND (:status IS NULL OR p.status = CAST(:status AS VARCHAR)) " +
            "AND (CAST(:merchantId AS UUID) IS NULL OR p.merchant_id = CAST(:merchantId AS UUID)) " +
            "AND (:reconciled IS NULL OR p.reconciled = :reconciled) " +
            "AND (CAST(:startDate AS TIMESTAMP) IS NULL OR p.created_at >= CAST(:startDate AS TIMESTAMP)) " +
            "AND (CAST(:endDate AS TIMESTAMP) IS NULL OR p.created_at <= CAST(:endDate AS TIMESTAMP)) " +
            "ORDER BY p.created_at DESC",
            countQuery = "SELECT COUNT(*) FROM payments p WHERE p.distributor_id = :distributorId " +
            "AND (:status IS NULL OR p.status = CAST(:status AS VARCHAR)) " +
            "AND (CAST(:merchantId AS UUID) IS NULL OR p.merchant_id = CAST(:merchantId AS UUID)) " +
            "AND (:reconciled IS NULL OR p.reconciled = :reconciled) " +
            "AND (CAST(:startDate AS TIMESTAMP) IS NULL OR p.created_at >= CAST(:startDate AS TIMESTAMP)) " +
            "AND (CAST(:endDate AS TIMESTAMP) IS NULL OR p.created_at <= CAST(:endDate AS TIMESTAMP))",
            nativeQuery = true)
    Page<Payment> findByFilters(
            @Param("distributorId") UUID distributorId,
            @Param("status") String status,
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

    @Query(value = "SELECT COALESCE(MAX(CAST(SUBSTRING(external_reference, LENGTH(:prefix) + 1) AS INTEGER)), 0) " +
            "FROM payments WHERE external_reference LIKE :prefix%", nativeQuery = true)
    Integer findMaxCashReferenceByPrefix(@Param("prefix") String prefix);

    /** Scope to a merchant brand (MERCHANT_ADMIN). */
    Page<Payment> findByDistributorMerchantId(UUID merchantId, Pageable pageable);

    @Query("SELECT p FROM Payment p WHERE p.distributor.merchant.id = :merchantId " +
            "AND (p.paymentNumber LIKE %:search% OR p.externalReference LIKE %:search% " +
            "OR (p.merchant IS NOT NULL AND p.merchant.businessName LIKE %:search%))")
    Page<Payment> searchPaymentsByMerchant(
            @Param("merchantId") UUID merchantId,
            @Param("search") String search,
            Pageable pageable);

    // Global queries for SUPER_ADMIN/ADMIN (no distributor filter)
    @Query("SELECT COUNT(p) FROM Payment p WHERE p.reconciled = false")
    long countAllUnreconciledPayments();

    // AI Feature Engineering - Historical queries
    @Query("SELECT p FROM Payment p WHERE p.merchant.id = :merchantId AND p.createdAt < :asOfDate")
    List<Payment> findByMerchantIdAndCreatedAtBefore(
            @Param("merchantId") UUID merchantId,
            @Param("asOfDate") LocalDateTime asOfDate);
}
