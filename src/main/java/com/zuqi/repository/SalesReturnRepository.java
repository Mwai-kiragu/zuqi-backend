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
import java.util.UUID;

@Repository
public interface SalesReturnRepository extends JpaRepository<SalesReturn, UUID> {

    Page<SalesReturn> findByDistributorId(UUID distributorId, Pageable pageable);

    Page<SalesReturn> findByDistributorMerchantId(UUID merchantId, Pageable pageable);

    boolean existsByReturnNumber(String returnNumber);

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

    // ── Duplicate / over-return guards ──────────────────────────────────────

    /** Sum of totalAmount for all non-CANCELLED returns against a given invoice. */
    @Query("SELECT COALESCE(SUM(r.totalAmount), 0) FROM SalesReturn r " +
           "WHERE r.invoice.id = :invoiceId AND r.status <> 'CANCELLED'")
    BigDecimal sumActiveReturnedAmountByInvoiceId(@Param("invoiceId") UUID invoiceId);

    // ── Export (all, no pagination) ─────────────────────────────────────────

    List<SalesReturn> findByDistributorIdOrderByCreatedAtDesc(UUID distributorId);

    List<SalesReturn> findByDistributorMerchantIdOrderByCreatedAtDesc(UUID merchantId);
}
