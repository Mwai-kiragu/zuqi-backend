package com.zuqi.repository;

import com.zuqi.domain.invoice.Invoice;
import com.zuqi.domain.invoice.InvoiceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);

    Optional<Invoice> findByOrderId(UUID orderId);

    Page<Invoice> findByDistributorId(UUID distributorId, Pageable pageable);

    Page<Invoice> findByMerchantId(UUID merchantId, Pageable pageable);

    Page<Invoice> findByDistributorIdAndStatus(UUID distributorId, InvoiceStatus status, Pageable pageable);

    @Query(value = "SELECT * FROM invoices i WHERE " +
           "(CAST(:distributorId AS UUID) IS NULL OR i.distributor_id = CAST(:distributorId AS UUID)) " +
           "AND (:status IS NULL OR i.status = CAST(:status AS VARCHAR)) " +
           "AND (CAST(:merchantId AS UUID) IS NULL OR i.merchant_id = CAST(:merchantId AS UUID)) " +
           "AND (CAST(:startDate AS DATE) IS NULL OR i.issue_date >= CAST(:startDate AS DATE)) " +
           "AND (CAST(:endDate AS DATE) IS NULL OR i.issue_date <= CAST(:endDate AS DATE))",
           countQuery = "SELECT COUNT(*) FROM invoices i WHERE " +
           "(CAST(:distributorId AS UUID) IS NULL OR i.distributor_id = CAST(:distributorId AS UUID)) " +
           "AND (:status IS NULL OR i.status = CAST(:status AS VARCHAR)) " +
           "AND (CAST(:merchantId AS UUID) IS NULL OR i.merchant_id = CAST(:merchantId AS UUID)) " +
           "AND (CAST(:startDate AS DATE) IS NULL OR i.issue_date >= CAST(:startDate AS DATE)) " +
           "AND (CAST(:endDate AS DATE) IS NULL OR i.issue_date <= CAST(:endDate AS DATE))",
           nativeQuery = true)
    Page<Invoice> findByFilters(
            @Param("distributorId") UUID distributorId,
            @Param("status") String status,
            @Param("merchantId") UUID merchantId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            Pageable pageable);

    @Query("SELECT i FROM Invoice i WHERE i.dueDate < :today " +
           "AND i.status NOT IN ('PAID', 'CANCELLED')")
    List<Invoice> findOverdueInvoices(@Param("today") LocalDate today);

    @Query(value = "SELECT i FROM Invoice i WHERE " +
           "(:distributorId IS NULL OR i.distributor.id = :distributorId) " +
           "AND (LOWER(i.invoiceNumber) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(i.merchant.businessName) LIKE LOWER(CONCAT('%', :search, '%')))",
           countQuery = "SELECT COUNT(i) FROM Invoice i WHERE " +
           "(:distributorId IS NULL OR i.distributor.id = :distributorId) " +
           "AND (LOWER(i.invoiceNumber) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(i.merchant.businessName) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Invoice> searchInvoices(
            @Param("distributorId") UUID distributorId,
            @Param("search") String search,
            Pageable pageable);

    @Query("SELECT COALESCE(MAX(CAST(SUBSTRING(i.invoiceNumber, LENGTH(:prefix) + 1) AS integer)), 0) " +
           "FROM Invoice i WHERE i.invoiceNumber LIKE CONCAT(:prefix, '%')")
    Integer findMaxInvoiceNumberByPrefix(@Param("prefix") String prefix);

    long countByDistributorIdAndStatus(UUID distributorId, InvoiceStatus status);

    long countByStatus(InvoiceStatus status);

    long countByDistributorId(UUID distributorId);
}
