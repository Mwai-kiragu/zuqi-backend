package com.zuqi.repository;

import com.zuqi.domain.supplier.SupplierBill;
import com.zuqi.domain.supplier.SupplierBillStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SupplierBillRepository extends JpaRepository<SupplierBill, UUID> {

    Page<SupplierBill> findByDistributorIdOrderByCreatedAtDesc(
            @Param("distributorId") UUID distributorId, Pageable pageable);

    Page<SupplierBill> findByDistributorIdAndStatusOrderByCreatedAtDesc(
            UUID distributorId, SupplierBillStatus status, Pageable pageable);

    @Query("SELECT sb FROM SupplierBill sb WHERE sb.distributor.id IN " +
           "(SELECT d.id FROM Distributor d WHERE d.merchant.id = :merchantId) " +
           "ORDER BY sb.createdAt DESC")
    Page<SupplierBill> findByDistributorMerchantId(
            @Param("merchantId") UUID merchantId, Pageable pageable);

    @Query("SELECT sb FROM SupplierBill sb WHERE sb.distributor.id IN " +
           "(SELECT d.id FROM Distributor d WHERE d.merchant.id = :merchantId) " +
           "AND sb.status = :status ORDER BY sb.createdAt DESC")
    Page<SupplierBill> findByDistributorMerchantIdAndStatus(
            @Param("merchantId") UUID merchantId,
            @Param("status") SupplierBillStatus status,
            Pageable pageable);

    Page<SupplierBill> findBySupplierIdAndDistributorIdOrderByCreatedAtDesc(
            UUID supplierId, UUID distributorId, Pageable pageable);

    @Query("SELECT sb FROM SupplierBill sb WHERE sb.supplier.id = :supplierId " +
           "AND sb.distributor.id IN " +
           "(SELECT d.id FROM Distributor d WHERE d.merchant.id = :merchantId) " +
           "ORDER BY sb.createdAt DESC")
    Page<SupplierBill> findBySupplierIdAndMerchantId(
            @Param("supplierId") UUID supplierId,
            @Param("merchantId") UUID merchantId,
            Pageable pageable);

    /** Outstanding bills (not fully paid/cancelled) for a supplier — used in FT payment selection */
    @Query("SELECT sb FROM SupplierBill sb WHERE sb.supplier.id = :supplierId " +
           "AND sb.distributor.id = :distributorId " +
           "AND sb.status NOT IN ('PAID', 'CANCELLED') " +
           "ORDER BY sb.dueDate ASC NULLS LAST")
    List<SupplierBill> findOutstandingBySupplierAndDistributor(
            @Param("supplierId") UUID supplierId,
            @Param("distributorId") UUID distributorId);

    @Query("SELECT COALESCE(SUM(sb.totalAmount - sb.paidAmount), 0) FROM SupplierBill sb " +
           "WHERE sb.distributor.id = :distributorId " +
           "AND sb.status NOT IN ('PAID', 'CANCELLED')")
    BigDecimal sumOutstandingByDistributorId(@Param("distributorId") UUID distributorId);

    @Query("SELECT sb FROM SupplierBill sb WHERE sb.dueDate < :today " +
           "AND sb.status NOT IN ('PAID', 'CANCELLED')")
    List<SupplierBill> findOverdueBills(@Param("today") LocalDate today);

    @Query("SELECT MAX(sb.billNumber) FROM SupplierBill sb WHERE sb.billNumber LIKE 'SBILL-%'")
    Optional<String> findMaxBillNumber();

    boolean existsByBillNumber(String billNumber);
}
