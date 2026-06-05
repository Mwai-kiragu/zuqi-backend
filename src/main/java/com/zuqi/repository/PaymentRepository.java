package com.zuqi.repository;

import com.zuqi.domain.payment.Payment;
import com.zuqi.domain.payment.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
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
            "AND (:paymentMethodId IS NULL OR p.payment_method_id = :paymentMethodId) " +
            "AND (CAST(:startDate AS TIMESTAMP) IS NULL OR p.created_at >= CAST(:startDate AS TIMESTAMP)) " +
            "AND (CAST(:endDate AS TIMESTAMP) IS NULL OR p.created_at <= CAST(:endDate AS TIMESTAMP)) " +
            "ORDER BY p.created_at DESC",
            countQuery = "SELECT COUNT(*) FROM payments p WHERE p.distributor_id = :distributorId " +
            "AND (:status IS NULL OR p.status = CAST(:status AS VARCHAR)) " +
            "AND (CAST(:merchantId AS UUID) IS NULL OR p.merchant_id = CAST(:merchantId AS UUID)) " +
            "AND (:reconciled IS NULL OR p.reconciled = :reconciled) " +
            "AND (:paymentMethodId IS NULL OR p.payment_method_id = :paymentMethodId) " +
            "AND (CAST(:startDate AS TIMESTAMP) IS NULL OR p.created_at >= CAST(:startDate AS TIMESTAMP)) " +
            "AND (CAST(:endDate AS TIMESTAMP) IS NULL OR p.created_at <= CAST(:endDate AS TIMESTAMP))",
            nativeQuery = true)
    Page<Payment> findByFilters(
            @Param("distributorId") UUID distributorId,
            @Param("status") String status,
            @Param("merchantId") UUID merchantId,
            @Param("reconciled") Boolean reconciled,
            @Param("paymentMethodId") Long paymentMethodId,
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

    @Query("SELECT COUNT(p) FROM Payment p WHERE p.distributor.id = :distributorId AND p.reconciled = false " +
            "AND p.createdAt >= :startDate AND p.createdAt <= :endDate")
    long countUnreconciledPaymentsInPeriod(@Param("distributorId") UUID distributorId,
                                            @Param("startDate") LocalDateTime startDate,
                                            @Param("endDate") LocalDateTime endDate);

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

    // AI feature queries (Phase 2 plan — Section 1.4)
    @Query("SELECT p FROM Payment p WHERE p.distributor.id = :distributorId " +
           "AND p.paymentDate >= :startDate AND p.paymentDate <= :endDate")
    List<Payment> findByDistributorIdAndPaymentDateBetween(
            @Param("distributorId") UUID distributorId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /** Cash-flow forecast: completed payments since a given date for baseline calculation. */
    @Query("SELECT p FROM Payment p WHERE p.distributor.id = :distributorId " +
           "AND p.status = com.zuqi.domain.payment.PaymentStatus.COMPLETED " +
           "AND p.createdAt >= :since")
    List<Payment> findCompletedSince(
            @Param("distributorId") UUID distributorId,
            @Param("since") LocalDateTime since);

    /** Total amount collected (COMPLETED) in a date range. */
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p " +
           "WHERE p.distributor.id = :distributorId AND p.status = 'COMPLETED' " +
           "AND p.paymentDate >= :startDate AND p.paymentDate <= :endDate")
    BigDecimal sumCollectedByDistributorAndDateRange(
            @Param("distributorId") UUID distributorId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /** Count of COMPLETED payments in a date range. */
    @Query("SELECT COUNT(p) FROM Payment p " +
           "WHERE p.distributor.id = :distributorId AND p.status = 'COMPLETED' " +
           "AND p.paymentDate >= :startDate AND p.paymentDate <= :endDate")
    long countCompletedByDistributorAndDateRange(
            @Param("distributorId") UUID distributorId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /** Breakdown by payment method: [methodCode, methodName, count, total]. */
    @Query("SELECT p.paymentMethod.code, p.paymentMethod.name, COUNT(p), COALESCE(SUM(p.amount), 0) " +
           "FROM Payment p WHERE p.distributor.id = :distributorId AND p.status = 'COMPLETED' " +
           "AND p.paymentDate >= :startDate AND p.paymentDate <= :endDate " +
           "GROUP BY p.paymentMethod.code, p.paymentMethod.name")
    List<Object[]> summaryByPaymentMethod(
            @Param("distributorId") UUID distributorId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /** Daily collections: [date, count, total]. */
    @Query("SELECT CAST(p.paymentDate AS LocalDate), COUNT(p), COALESCE(SUM(p.amount), 0) " +
           "FROM Payment p WHERE p.distributor.id = :distributorId AND p.status = 'COMPLETED' " +
           "AND p.paymentDate >= :startDate AND p.paymentDate <= :endDate " +
           "GROUP BY CAST(p.paymentDate AS LocalDate) ORDER BY CAST(p.paymentDate AS LocalDate)")
    List<Object[]> dailyCollections(
            @Param("distributorId") UUID distributorId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    // ── Payment stats (aggregate totals for stat cards) ──────────────────────

    interface PaymentStatsView {
        BigDecimal getTotalAmount();
        BigDecimal getCompletedAmount();
        BigDecimal getPendingAmount();
        Long getPaymentCount();
    }

    @Query(value = "SELECT " +
            "COALESCE(SUM(p.amount), 0) AS total_amount, " +
            "COALESCE(SUM(CASE WHEN p.status = 'COMPLETED' THEN p.amount ELSE 0 END), 0) AS completed_amount, " +
            "COALESCE(SUM(CASE WHEN p.status = 'PENDING' THEN p.amount ELSE 0 END), 0) AS pending_amount, " +
            "COUNT(*) AS payment_count " +
            "FROM payments p WHERE p.distributor_id = :distributorId " +
            "AND (:status IS NULL OR p.status = CAST(:status AS VARCHAR)) " +
            "AND (CAST(:merchantId AS UUID) IS NULL OR p.merchant_id = CAST(:merchantId AS UUID)) " +
            "AND (:reconciled IS NULL OR p.reconciled = :reconciled) " +
            "AND (:paymentMethodId IS NULL OR p.payment_method_id = :paymentMethodId) " +
            "AND (CAST(:startDate AS TIMESTAMP) IS NULL OR p.created_at >= CAST(:startDate AS TIMESTAMP)) " +
            "AND (CAST(:endDate AS TIMESTAMP) IS NULL OR p.created_at <= CAST(:endDate AS TIMESTAMP))",
            nativeQuery = true)
    PaymentStatsView statsForDistributorByFilters(
            @Param("distributorId") UUID distributorId,
            @Param("status") String status,
            @Param("merchantId") UUID merchantId,
            @Param("reconciled") Boolean reconciled,
            @Param("paymentMethodId") Long paymentMethodId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Query(value = "SELECT " +
            "COALESCE(SUM(p.amount), 0) AS total_amount, " +
            "COALESCE(SUM(CASE WHEN p.status = 'COMPLETED' THEN p.amount ELSE 0 END), 0) AS completed_amount, " +
            "COALESCE(SUM(CASE WHEN p.status = 'PENDING' THEN p.amount ELSE 0 END), 0) AS pending_amount, " +
            "COUNT(*) AS payment_count " +
            "FROM payments p " +
            "JOIN distributors d ON p.distributor_id = d.id " +
            "WHERE d.merchant_id = :merchantId",
            nativeQuery = true)
    PaymentStatsView statsForMerchant(@Param("merchantId") UUID merchantId);

    @Query(value = "SELECT " +
            "COALESCE(SUM(p.amount), 0) AS total_amount, " +
            "COALESCE(SUM(CASE WHEN p.status = 'COMPLETED' THEN p.amount ELSE 0 END), 0) AS completed_amount, " +
            "COALESCE(SUM(CASE WHEN p.status = 'PENDING' THEN p.amount ELSE 0 END), 0) AS pending_amount, " +
            "COUNT(*) AS payment_count " +
            "FROM payments p",
            nativeQuery = true)
    PaymentStatsView statsAll();

    // ── Export (all, no pagination) ─────────────────────────────────────────
    List<Payment> findByDistributorIdOrderByCreatedAtDesc(UUID distributorId);

    @Query("SELECT p FROM Payment p WHERE p.distributor.merchant.id = :merchantId ORDER BY p.createdAt DESC")
    List<Payment> findByDistributorMerchantIdOrderByCreatedAtDesc(@Param("merchantId") UUID merchantId);
}
