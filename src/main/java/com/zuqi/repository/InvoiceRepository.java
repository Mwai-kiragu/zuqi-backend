package com.zuqi.repository;

import com.zuqi.domain.invoice.Invoice;
import com.zuqi.domain.invoice.InvoiceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);

    Optional<Invoice> findByOrderId(UUID orderId);

    Optional<Invoice> findByPosOrderId(UUID posOrderId);

    Page<Invoice> findByDistributorId(UUID distributorId, Pageable pageable);

    Page<Invoice> findByMerchantId(UUID merchantId, Pageable pageable);

    Page<Invoice> findByDistributorIdAndStatus(UUID distributorId, InvoiceStatus status, Pageable pageable);

    @Query(value = "SELECT * FROM invoices i WHERE " +
           "(CAST(:distributorId AS UUID) IS NULL OR i.distributor_id = CAST(:distributorId AS UUID)) " +
           "AND (:status IS NULL OR i.status = CAST(:status AS VARCHAR)) " +
           "AND (CAST(:merchantId AS UUID) IS NULL OR i.merchant_id = CAST(:merchantId AS UUID)) " +
           "AND (CAST(:startDate AS DATE) IS NULL OR i.issue_date >= CAST(:startDate AS DATE)) " +
           "AND (CAST(:endDate AS DATE) IS NULL OR i.issue_date <= CAST(:endDate AS DATE)) " +
           "ORDER BY i.created_at DESC",
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

    /** Safely finds the max sequential number for INV-XXXX format, ignoring old date-based entries. */
    @Query(value = "SELECT COALESCE(MAX(CAST(SUBSTRING(invoice_number, 5) AS INTEGER)), 0) " +
                   "FROM invoices WHERE invoice_number ~ '^INV-[0-9]+$'",
           nativeQuery = true)
    Integer findMaxStandardInvoiceNumber();

    long countByDistributorIdAndStatus(UUID distributorId, InvoiceStatus status);

    long countByStatus(InvoiceStatus status);

    long countByDistributorId(UUID distributorId);

    @Query("SELECT i FROM Invoice i LEFT JOIN FETCH i.merchant WHERE i.distributor.id = :distributorId " +
           "AND i.status NOT IN ('PAID', 'CANCELLED')")
    List<Invoice> findUnpaidByDistributorId(@Param("distributorId") UUID distributorId);

    @Query("SELECT i FROM Invoice i LEFT JOIN FETCH i.merchant WHERE i.distributor.merchant.id = :merchantId " +
           "AND i.status NOT IN ('PAID', 'CANCELLED')")
    List<Invoice> findUnpaidByMerchantId(@Param("merchantId") UUID merchantId);

    /** Scope to a merchant brand (MERCHANT_ADMIN). */
    Page<Invoice> findByDistributorMerchantId(UUID merchantId, Pageable pageable);

    long countByDistributorMerchantIdAndStatus(UUID merchantId, InvoiceStatus status);

    long countByDistributorMerchantId(UUID merchantId);

    @Query("SELECT i FROM Invoice i WHERE i.distributor.merchant.id = :merchantId " +
           "AND (LOWER(i.invoiceNumber) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR (i.merchant IS NOT NULL AND LOWER(i.merchant.businessName) LIKE LOWER(CONCAT('%', :search, '%'))))")
    Page<Invoice> searchInvoicesByMerchant(
            @Param("merchantId") UUID merchantId,
            @Param("search") String search,
            Pageable pageable);

    /** Sum of PAID invoices in a date range for a distributor (revenue). */
    @Query("SELECT COALESCE(SUM(i.totalAmount), 0) FROM Invoice i " +
           "WHERE i.distributor.id = :distributorId AND i.status = 'PAID' " +
           "AND i.issueDate BETWEEN :from AND :to")
    BigDecimal sumPaidByDistributorAndDateRange(
            @Param("distributorId") UUID distributorId,
            @Param("from") java.time.LocalDate from,
            @Param("to") java.time.LocalDate to);

    /** Sum of PAID invoices in a date range for a merchant brand. */
    @Query("SELECT COALESCE(SUM(i.totalAmount), 0) FROM Invoice i " +
           "WHERE i.distributor.merchant.id = :merchantId AND i.status = 'PAID' " +
           "AND i.issueDate BETWEEN :from AND :to")
    BigDecimal sumPaidByMerchantAndDateRange(
            @Param("merchantId") UUID merchantId,
            @Param("from") java.time.LocalDate from,
            @Param("to") java.time.LocalDate to);

    /** AR balance: sum of unpaid invoice balanceDue for a distributor. */
    @Query("SELECT COALESCE(SUM(i.balanceDue), 0) FROM Invoice i " +
           "WHERE i.distributor.id = :distributorId " +
           "AND i.status NOT IN ('PAID', 'CANCELLED')")
    BigDecimal sumArBalanceByDistributor(@Param("distributorId") UUID distributorId);

    /** AR balance for a merchant brand. */
    @Query("SELECT COALESCE(SUM(i.balanceDue), 0) FROM Invoice i " +
           "WHERE i.distributor.merchant.id = :merchantId " +
           "AND i.status NOT IN ('PAID', 'CANCELLED')")
    BigDecimal sumArBalanceByMerchant(@Param("merchantId") UUID merchantId);

    /** Monthly revenue (PAID invoices) grouped by year+month. */
    @Query("SELECT YEAR(i.issueDate), MONTH(i.issueDate), COALESCE(SUM(i.totalAmount), 0) " +
           "FROM Invoice i WHERE i.distributor.id = :distributorId AND i.status = 'PAID' " +
           "AND i.issueDate >= :from GROUP BY YEAR(i.issueDate), MONTH(i.issueDate)")
    java.util.List<Object[]> monthlyRevenueByDistributor(
            @Param("distributorId") UUID distributorId,
            @Param("from") java.time.LocalDate from);

    /** Monthly revenue for merchant brand. */
    @Query("SELECT YEAR(i.issueDate), MONTH(i.issueDate), COALESCE(SUM(i.totalAmount), 0) " +
           "FROM Invoice i WHERE i.distributor.merchant.id = :merchantId AND i.status = 'PAID' " +
           "AND i.issueDate >= :from GROUP BY YEAR(i.issueDate), MONTH(i.issueDate)")
    java.util.List<Object[]> monthlyRevenueByMerchant(
            @Param("merchantId") UUID merchantId,
            @Param("from") java.time.LocalDate from);

    /** Get the brand Merchant ID for an invoice (for public pay endpoint, avoids lazy-loading). */
    @Query("SELECT i.distributor.merchant.id FROM Invoice i WHERE i.invoiceNumber = :invoiceNumber")
    Optional<UUID> findMerchantIdByInvoiceNumber(@Param("invoiceNumber") String invoiceNumber);
}
