package com.zuqi.repository;

import com.zuqi.domain.returns.ReturnStatus;
import com.zuqi.domain.returns.SalesReturn;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SalesReturnRepository extends JpaRepository<SalesReturn, UUID> {

    Page<SalesReturn> findByDistributorId(UUID distributorId, Pageable pageable);

    Page<SalesReturn> findByDistributorMerchantId(UUID merchantId, Pageable pageable);

    boolean existsByReturnNumber(String returnNumber);

    Optional<SalesReturn> findByReturnNumber(String returnNumber);

    // ── Stats aggregates ────────────────────────────────────────────────────

    long countByDistributorId(UUID distributorId);
    long countByDistributorIdAndStatus(UUID distributorId, ReturnStatus status);

    @Query("SELECT COALESCE(SUM(r.totalAmount), 0) FROM SalesReturn r WHERE r.distributor.id = :distributorId")
    BigDecimal sumTotalAmountByDistributorId(@Param("distributorId") UUID distributorId);

    long countByDistributorMerchantId(UUID merchantId);
    long countByDistributorMerchantIdAndStatus(UUID merchantId, ReturnStatus status);

    @Query("SELECT COALESCE(SUM(r.totalAmount), 0) FROM SalesReturn r WHERE r.distributor.merchant.id = :merchantId")
    BigDecimal sumTotalAmountByDistributorMerchantId(@Param("merchantId") UUID merchantId);

    long countByStatus(ReturnStatus status);

    @Query("SELECT COALESCE(SUM(r.totalAmount), 0) FROM SalesReturn r")
    BigDecimal sumTotalAmountAll();

    // ── Financial overview aggregates ────────────────────────────────────────

    /** Sum of CONFIRMED return amounts in a date range for a distributor (subtracted from revenue). */
    @Query("SELECT COALESCE(SUM(r.totalAmount), 0) FROM SalesReturn r " +
           "WHERE r.distributor.id = :distributorId AND r.status = com.zuqi.domain.returns.ReturnStatus.CONFIRMED " +
           "AND r.createdAt >= :from AND r.createdAt <= :to")
    BigDecimal sumConfirmedByDistributorAndDateRange(
            @Param("distributorId") UUID distributorId,
            @Param("from") java.time.LocalDateTime from,
            @Param("to") java.time.LocalDateTime to);

    /** Sum of CONFIRMED return amounts in a date range for a merchant brand (subtracted from revenue). */
    @Query("SELECT COALESCE(SUM(r.totalAmount), 0) FROM SalesReturn r " +
           "WHERE r.distributor.merchant.id = :merchantId AND r.status = com.zuqi.domain.returns.ReturnStatus.CONFIRMED " +
           "AND r.createdAt >= :from AND r.createdAt <= :to")
    BigDecimal sumConfirmedByMerchantAndDateRange(
            @Param("merchantId") UUID merchantId,
            @Param("from") java.time.LocalDateTime from,
            @Param("to") java.time.LocalDateTime to);

    /** Monthly CONFIRMED returns grouped by year+month for a distributor. */
    @Query("SELECT YEAR(r.createdAt), MONTH(r.createdAt), COALESCE(SUM(r.totalAmount), 0) " +
           "FROM SalesReturn r WHERE r.distributor.id = :distributorId " +
           "AND r.status = com.zuqi.domain.returns.ReturnStatus.CONFIRMED " +
           "AND r.createdAt >= :from GROUP BY YEAR(r.createdAt), MONTH(r.createdAt)")
    java.util.List<Object[]> monthlyConfirmedByDistributor(
            @Param("distributorId") UUID distributorId,
            @Param("from") java.time.LocalDateTime from);

    /** Monthly CONFIRMED returns grouped by year+month for a merchant brand. */
    @Query("SELECT YEAR(r.createdAt), MONTH(r.createdAt), COALESCE(SUM(r.totalAmount), 0) " +
           "FROM SalesReturn r WHERE r.distributor.merchant.id = :merchantId " +
           "AND r.status = com.zuqi.domain.returns.ReturnStatus.CONFIRMED " +
           "AND r.createdAt >= :from GROUP BY YEAR(r.createdAt), MONTH(r.createdAt)")
    java.util.List<Object[]> monthlyConfirmedByMerchant(
            @Param("merchantId") UUID merchantId,
            @Param("from") java.time.LocalDateTime from);

    // ── Duplicate / over-return guards ──────────────────────────────────────

    /** Sum of totalAmount for all non-CANCELLED returns against a given invoice. */
    @Query("SELECT COALESCE(SUM(r.totalAmount), 0) FROM SalesReturn r " +
           "WHERE r.invoice.id = :invoiceId AND r.status <> 'CANCELLED'")
    BigDecimal sumActiveReturnedAmountByInvoiceId(@Param("invoiceId") UUID invoiceId);

    /** Sum of totalAmount for all non-CANCELLED returns against a given order. */
    @Query("SELECT COALESCE(SUM(r.totalAmount), 0) FROM SalesReturn r " +
           "WHERE r.order.id = :orderId AND r.status <> com.zuqi.domain.returns.ReturnStatus.CANCELLED")
    BigDecimal sumActiveReturnedAmountByOrderId(@Param("orderId") UUID orderId);

    // ── Export (all, no pagination) ─────────────────────────────────────────

    List<SalesReturn> findByDistributorIdOrderByCreatedAtDesc(UUID distributorId);

    List<SalesReturn> findByDistributorMerchantIdOrderByCreatedAtDesc(UUID merchantId);
}
