package com.zuqi.repository;

import com.zuqi.domain.ft.FundsTransfer;
import com.zuqi.domain.ft.FundsTransferStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.UUID;

public interface FundsTransferRepository extends JpaRepository<FundsTransfer, UUID> {

    Page<FundsTransfer> findByDistributorIdOrderByCreatedAtDesc(UUID distributorId, Pageable pageable);

    Page<FundsTransfer> findByDistributorIdAndCreatedAtBetweenOrderByCreatedAtDesc(UUID distributorId, LocalDateTime from, LocalDateTime to, Pageable pageable);

    Page<FundsTransfer> findByDistributorIdAndStatusOrderByCreatedAtDesc(UUID distributorId, FundsTransferStatus status, Pageable pageable);

    Page<FundsTransfer> findByDistributorIdAndStatusAndCreatedAtBetweenOrderByCreatedAtDesc(UUID distributorId, FundsTransferStatus status, LocalDateTime from, LocalDateTime to, Pageable pageable);

    Page<FundsTransfer> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime from, LocalDateTime to, Pageable pageable);

    Page<FundsTransfer> findByStatusAndCreatedAtBetweenOrderByCreatedAtDesc(FundsTransferStatus status, LocalDateTime from, LocalDateTime to, Pageable pageable);

    // MERCHANT_ADMIN isolation
    @Query("SELECT ft FROM FundsTransfer ft WHERE ft.distributorId IN " +
           "(SELECT d.id FROM Distributor d WHERE d.merchant.id = :merchantId) " +
           "ORDER BY ft.createdAt DESC")
    Page<FundsTransfer> findByDistributorMerchantId(@Param("merchantId") UUID merchantId, Pageable pageable);

    @Query("SELECT ft FROM FundsTransfer ft WHERE ft.distributorId IN " +
           "(SELECT d.id FROM Distributor d WHERE d.merchant.id = :merchantId) " +
           "AND ft.createdAt BETWEEN :from AND :to ORDER BY ft.createdAt DESC")
    Page<FundsTransfer> findByDistributorMerchantIdAndDateRange(@Param("merchantId") UUID merchantId,
                                                                 @Param("from") LocalDateTime from,
                                                                 @Param("to") LocalDateTime to,
                                                                 Pageable pageable);

    @Query("SELECT ft FROM FundsTransfer ft WHERE ft.distributorId IN " +
           "(SELECT d.id FROM Distributor d WHERE d.merchant.id = :merchantId) " +
           "AND ft.status = :status ORDER BY ft.createdAt DESC")
    Page<FundsTransfer> findByDistributorMerchantIdAndStatus(@Param("merchantId") UUID merchantId,
                                                              @Param("status") FundsTransferStatus status,
                                                              Pageable pageable);

    @Query("SELECT ft FROM FundsTransfer ft WHERE ft.distributorId IN " +
           "(SELECT d.id FROM Distributor d WHERE d.merchant.id = :merchantId) " +
           "AND ft.status = :status AND ft.createdAt BETWEEN :from AND :to ORDER BY ft.createdAt DESC")
    Page<FundsTransfer> findByDistributorMerchantIdAndStatusAndDateRange(@Param("merchantId") UUID merchantId,
                                                                          @Param("status") FundsTransferStatus status,
                                                                          @Param("from") LocalDateTime from,
                                                                          @Param("to") LocalDateTime to,
                                                                          Pageable pageable);

    java.util.Optional<FundsTransfer> findByReferenceNumber(String referenceNumber);

    java.util.Optional<FundsTransfer> findByGatewayTransactionId(String gatewayTransactionId);

    // Transfers pending approval where current user is an approver at the current level
    @Query("SELECT ft FROM FundsTransfer ft WHERE ft.distributorId = :distributorId " +
           "AND ft.status = 'PENDING_APPROVAL' " +
           "AND EXISTS (SELECT al FROM FtApprovalLevel al WHERE al.amountRangeId = ft.amountRangeId " +
           "  AND al.levelNumber = ft.currentApprovalLevel AND al.approverUserId = :userId) " +
           "AND NOT EXISTS (SELECT a FROM FtApproval a WHERE a.transferId = ft.id " +
           "  AND a.levelNumber = ft.currentApprovalLevel AND a.approverId = :userId) " +
           "ORDER BY ft.createdAt DESC")
    Page<FundsTransfer> findPendingApprovalForUser(@Param("distributorId") UUID distributorId,
                                                    @Param("userId") UUID userId,
                                                    Pageable pageable);
}
