package com.zuqi.repository;

import com.zuqi.domain.inventory.StockTransfer;
import com.zuqi.domain.inventory.StockTransferStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface StockTransferRepository extends JpaRepository<StockTransfer, UUID> {

    Page<StockTransfer> findBySourceWarehouseIdOrDestinationWarehouseId(UUID sourceId, UUID destId, Pageable pageable);

    Page<StockTransfer> findByStatus(StockTransferStatus status, Pageable pageable);

    List<StockTransfer> findBySourceBranchIdOrDestinationBranchId(UUID sourceId, UUID destId);

    Page<StockTransfer> findBySourceBranchIdOrDestinationBranchId(UUID sourceId, UUID destId, Pageable pageable);

    Page<StockTransfer> findBySourceBranchIdAndStatus(UUID sourceBranchId, StockTransferStatus status, Pageable pageable);

    Page<StockTransfer> findByDestinationBranchIdAndStatus(UUID destBranchId, StockTransferStatus status, Pageable pageable);

    Page<StockTransfer> findByStatusAndCreatedAtBetween(StockTransferStatus status, LocalDateTime from, LocalDateTime to, Pageable pageable);

    Page<StockTransfer> findByCreatedAtBetween(LocalDateTime from, LocalDateTime to, Pageable pageable);

    @Query("SELECT t FROM StockTransfer t WHERE " +
            "(t.sourceBranch.id = :branchId OR t.destinationBranch.id = :branchId) " +
            "AND t.createdAt BETWEEN :from AND :to")
    Page<StockTransfer> findByBranchIdAndDateRange(
            @Param("branchId") UUID branchId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);

    /** Scope to a merchant brand (MERCHANT_ADMIN). */
    @Query("SELECT t FROM StockTransfer t WHERE " +
            "t.sourceWarehouse.distributor.merchant.id = :merchantId OR " +
            "t.destinationWarehouse.distributor.merchant.id = :merchantId")
    Page<StockTransfer> findByMerchantId(@Param("merchantId") UUID merchantId, Pageable pageable);

    @Query("SELECT t FROM StockTransfer t WHERE t.status = :status AND (" +
            "t.sourceWarehouse.distributor.merchant.id = :merchantId OR " +
            "t.destinationWarehouse.distributor.merchant.id = :merchantId)")
    Page<StockTransfer> findByMerchantIdAndStatus(
            @Param("merchantId") UUID merchantId,
            @Param("status") StockTransferStatus status,
            Pageable pageable);

    @Query("SELECT t FROM StockTransfer t WHERE " +
            "(t.sourceWarehouse.distributor.merchant.id = :merchantId OR " +
            "t.destinationWarehouse.distributor.merchant.id = :merchantId) " +
            "AND t.createdAt BETWEEN :from AND :to")
    Page<StockTransfer> findByMerchantIdAndDateRange(
            @Param("merchantId") UUID merchantId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);

    @Query("SELECT t FROM StockTransfer t WHERE t.status = :status AND (" +
            "t.sourceWarehouse.distributor.merchant.id = :merchantId OR " +
            "t.destinationWarehouse.distributor.merchant.id = :merchantId) " +
            "AND t.createdAt BETWEEN :from AND :to")
    Page<StockTransfer> findByMerchantIdAndStatusAndDateRange(
            @Param("merchantId") UUID merchantId,
            @Param("status") StockTransferStatus status,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);
}
