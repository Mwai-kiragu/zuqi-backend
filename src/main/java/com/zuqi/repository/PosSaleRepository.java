package com.zuqi.repository;

import com.zuqi.domain.pos.PosSale;
import com.zuqi.domain.pos.PosSaleStatus;
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
public interface PosSaleRepository extends JpaRepository<PosSale, UUID> {

    Page<PosSale> findByBranchId(UUID branchId, Pageable pageable);

    Page<PosSale> findByBranchIdAndStatus(UUID branchId, PosSaleStatus status, Pageable pageable);

    Page<PosSale> findByStatus(PosSaleStatus status, Pageable pageable);

    Page<PosSale> findByShiftId(UUID shiftId, Pageable pageable);

    Optional<PosSale> findByReceiptNumber(String receiptNumber);

    @Query(value = "SELECT MAX(CAST(SUBSTRING(receipt_number FROM LENGTH(:prefix) + 1) AS INTEGER)) " +
                   "FROM pos_sales WHERE receipt_number LIKE CONCAT(:prefix, '%')", nativeQuery = true)
    Integer findMaxReceiptNumberByPrefix(@Param("prefix") String prefix);

    @Query("SELECT COUNT(s) FROM PosSale s WHERE s.branch.id = :branchId AND s.status = :status " +
           "AND s.createdAt BETWEEN :from AND :to")
    long countByBranchAndStatusAndDateRange(@Param("branchId") UUID branchId,
                                            @Param("status") PosSaleStatus status,
                                            @Param("from") LocalDateTime from,
                                            @Param("to") LocalDateTime to);

    @Query("SELECT COALESCE(SUM(s.totalAmount), 0) FROM PosSale s WHERE s.branch.id = :branchId " +
           "AND s.status = :status AND s.createdAt BETWEEN :from AND :to")
    BigDecimal sumTotalByBranchAndStatusAndDateRange(@Param("branchId") UUID branchId,
                                                     @Param("status") PosSaleStatus status,
                                                     @Param("from") LocalDateTime from,
                                                     @Param("to") LocalDateTime to);

    @Query("SELECT COUNT(s) FROM PosSale s WHERE s.branch.id = :branchId " +
           "AND s.status = com.zuqi.domain.pos.PosSaleStatus.COMPLETED " +
           "AND s.amountPaid > 0 AND s.amountPaid < s.totalAmount " +
           "AND s.createdAt BETWEEN :from AND :to")
    long countPartiallyPaidByBranchAndDateRange(@Param("branchId") UUID branchId,
                                                @Param("from") LocalDateTime from,
                                                @Param("to") LocalDateTime to);

    @Query("SELECT COALESCE(SUM(s.totalAmount - s.amountPaid), 0) FROM PosSale s WHERE s.branch.id = :branchId " +
           "AND s.status = com.zuqi.domain.pos.PosSaleStatus.COMPLETED " +
           "AND s.amountPaid > 0 AND s.amountPaid < s.totalAmount " +
           "AND s.createdAt BETWEEN :from AND :to")
    BigDecimal sumBalanceDuePartiallyPaidByBranchAndDateRange(@Param("branchId") UUID branchId,
                                                              @Param("from") LocalDateTime from,
                                                              @Param("to") LocalDateTime to);

    List<PosSale> findByBranchIdAndStatusAndCreatedAtBetween(UUID branchId, PosSaleStatus status,
                                                              LocalDateTime from, LocalDateTime to);

    // Paged date-range queries used by getSales with date filter
    Page<PosSale> findByBranchIdAndCreatedAtBetween(UUID branchId, LocalDateTime from, LocalDateTime to, Pageable pageable);

    @Query("SELECT s FROM PosSale s WHERE s.branch.id = :branchId AND s.status = :status AND s.createdAt BETWEEN :from AND :to")
    Page<PosSale> findByBranchIdAndStatusAndDateRange(@Param("branchId") UUID branchId,
                                                      @Param("status") PosSaleStatus status,
                                                      @Param("from") LocalDateTime from,
                                                      @Param("to") LocalDateTime to,
                                                      Pageable pageable);

    Page<PosSale> findByCreatedAtBetween(LocalDateTime from, LocalDateTime to, Pageable pageable);

    @Query("SELECT s FROM PosSale s WHERE s.status = :status AND s.createdAt BETWEEN :from AND :to")
    Page<PosSale> findByStatusAndDateRange(@Param("status") PosSaleStatus status,
                                           @Param("from") LocalDateTime from,
                                           @Param("to") LocalDateTime to,
                                           Pageable pageable);

    @Query("SELECT s FROM PosSale s JOIN FETCH s.branch WHERE s.branch.distributor.id = :distributorId ORDER BY s.createdAt DESC")
    List<PosSale> findAllByDistributorIdFetched(@Param("distributorId") UUID distributorId);

    @Query("SELECT s FROM PosSale s JOIN FETCH s.branch WHERE s.branch.distributor.merchant.id = :merchantId ORDER BY s.createdAt DESC")
    List<PosSale> findAllByMerchantIdFetched(@Param("merchantId") UUID merchantId);

    @Query("SELECT s FROM PosSale s JOIN FETCH s.branch ORDER BY s.createdAt DESC")
    List<PosSale> findAllFetched();

    // ── Financial overview queries ────────────────────────────────────────────

    @Query("SELECT COALESCE(SUM(s.totalAmount), 0) FROM PosSale s " +
           "WHERE s.branch.distributor.id = :distributorId " +
           "AND s.status = com.zuqi.domain.pos.PosSaleStatus.COMPLETED " +
           "AND s.createdAt >= :from AND s.createdAt <= :to")
    BigDecimal sumCompletedByDistributorAndDateRange(
            @Param("distributorId") UUID distributorId,
            @Param("from") java.time.LocalDateTime from,
            @Param("to") java.time.LocalDateTime to);

    @Query("SELECT COALESCE(SUM(s.totalAmount), 0) FROM PosSale s " +
           "WHERE s.branch.distributor.merchant.id = :merchantId " +
           "AND s.status = com.zuqi.domain.pos.PosSaleStatus.COMPLETED " +
           "AND s.createdAt >= :from AND s.createdAt <= :to")
    BigDecimal sumCompletedByMerchantAndDateRange(
            @Param("merchantId") UUID merchantId,
            @Param("from") java.time.LocalDateTime from,
            @Param("to") java.time.LocalDateTime to);

    @Query("SELECT YEAR(s.createdAt), MONTH(s.createdAt), COALESCE(SUM(s.totalAmount), 0) " +
           "FROM PosSale s WHERE s.branch.distributor.id = :distributorId " +
           "AND s.status = com.zuqi.domain.pos.PosSaleStatus.COMPLETED " +
           "AND s.createdAt >= :from " +
           "GROUP BY YEAR(s.createdAt), MONTH(s.createdAt)")
    List<Object[]> monthlyCompletedByDistributor(
            @Param("distributorId") UUID distributorId,
            @Param("from") java.time.LocalDateTime from);

    @Query("SELECT YEAR(s.createdAt), MONTH(s.createdAt), COALESCE(SUM(s.totalAmount), 0) " +
           "FROM PosSale s WHERE s.branch.distributor.merchant.id = :merchantId " +
           "AND s.status = com.zuqi.domain.pos.PosSaleStatus.COMPLETED " +
           "AND s.createdAt >= :from " +
           "GROUP BY YEAR(s.createdAt), MONTH(s.createdAt)")
    List<Object[]> monthlyCompletedByMerchant(
            @Param("merchantId") UUID merchantId,
            @Param("from") java.time.LocalDateTime from);
}
