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

    Optional<Invoice> findByInvoiceNumberAndDistributorId(String invoiceNumber, UUID distributorId);

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

    /** Per-distributor max sequence for a given prefix (e.g. "GN-INV-"). */
    @Query("SELECT COALESCE(MAX(CAST(SUBSTRING(i.invoiceNumber, LENGTH(:prefix) + 1) AS integer)), 0) " +
           "FROM Invoice i WHERE i.invoiceNumber LIKE CONCAT(:prefix, '%') AND i.distributor.id = :distributorId")
    Integer findMaxInvoiceNumberByDistributorAndPrefix(
            @Param("distributorId") UUID distributorId,
            @Param("prefix") String prefix);

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

    /** Sum of collected revenue (PAID + PARTIALLY_PAID) in a date range for a distributor. */
    @Query("SELECT COALESCE(SUM(i.paidAmount), 0) FROM Invoice i " +
           "WHERE i.distributor.id = :distributorId AND i.status IN ('PAID', 'PARTIALLY_PAID') " +
           "AND i.issueDate BETWEEN :from AND :to")
    BigDecimal sumPaidByDistributorAndDateRange(
            @Param("distributorId") UUID distributorId,
            @Param("from") java.time.LocalDate from,
            @Param("to") java.time.LocalDate to);

    /** Sum of collected revenue (PAID + PARTIALLY_PAID) in a date range for a merchant brand. */
    @Query("SELECT COALESCE(SUM(i.paidAmount), 0) FROM Invoice i " +
           "WHERE i.distributor.merchant.id = :merchantId AND i.status IN ('PAID', 'PARTIALLY_PAID') " +
           "AND i.issueDate BETWEEN :from AND :to")
    BigDecimal sumPaidByMerchantAndDateRange(
            @Param("merchantId") UUID merchantId,
            @Param("from") java.time.LocalDate from,
            @Param("to") java.time.LocalDate to);

    /** AR balance: sum of outstanding (totalAmount - paidAmount) for a distributor. */
    @Query("SELECT COALESCE(SUM(i.totalAmount - COALESCE(i.paidAmount, 0)), 0) FROM Invoice i " +
           "WHERE i.distributor.id = :distributorId " +
           "AND i.status NOT IN ('PAID', 'CANCELLED', 'DRAFT')")
    BigDecimal sumArBalanceByDistributor(@Param("distributorId") UUID distributorId);

    /** AR balance for a merchant brand. */
    @Query("SELECT COALESCE(SUM(i.totalAmount - COALESCE(i.paidAmount, 0)), 0) FROM Invoice i " +
           "WHERE i.distributor.merchant.id = :merchantId " +
           "AND i.status NOT IN ('PAID', 'CANCELLED', 'DRAFT')")
    BigDecimal sumArBalanceByMerchant(@Param("merchantId") UUID merchantId);

    /** Monthly collected revenue (PAID + PARTIALLY_PAID) grouped by year+month. */
    @Query("SELECT YEAR(i.issueDate), MONTH(i.issueDate), COALESCE(SUM(i.paidAmount), 0) " +
           "FROM Invoice i WHERE i.distributor.id = :distributorId AND i.status IN ('PAID', 'PARTIALLY_PAID') " +
           "AND i.issueDate >= :from GROUP BY YEAR(i.issueDate), MONTH(i.issueDate)")
    java.util.List<Object[]> monthlyRevenueByDistributor(
            @Param("distributorId") UUID distributorId,
            @Param("from") java.time.LocalDate from);

    /** Monthly collected revenue for merchant brand. */
    @Query("SELECT YEAR(i.issueDate), MONTH(i.issueDate), COALESCE(SUM(i.paidAmount), 0) " +
           "FROM Invoice i WHERE i.distributor.merchant.id = :merchantId AND i.status IN ('PAID', 'PARTIALLY_PAID') " +
           "AND i.issueDate >= :from GROUP BY YEAR(i.issueDate), MONTH(i.issueDate)")
    java.util.List<Object[]> monthlyRevenueByMerchant(
            @Param("merchantId") UUID merchantId,
            @Param("from") java.time.LocalDate from);

    /** Get the brand Merchant ID for an invoice (for public pay endpoint, avoids lazy-loading). */
    @Query("SELECT i.distributor.merchant.id FROM Invoice i WHERE i.invoiceNumber = :invoiceNumber")
    Optional<UUID> findMerchantIdByInvoiceNumber(@Param("invoiceNumber") String invoiceNumber);

    /** Sum of outstanding invoice balance for a customer (credit limit check). */
    @Query("SELECT COALESCE(SUM(i.totalAmount - i.paidAmount), 0) FROM Invoice i " +
           "WHERE i.merchant.id = :customerId AND i.status NOT IN ('PAID', 'CANCELLED')")
    BigDecimal sumUnpaidByCustomerId(@Param("customerId") UUID customerId);

    /** Batch version: returns [customerId, outstandingInvoiceBalance] pairs. */
    @Query("SELECT i.merchant.id, COALESCE(SUM(i.totalAmount - COALESCE(i.paidAmount, 0)), 0) FROM Invoice i " +
           "WHERE i.merchant.id IN :customerIds AND i.status NOT IN ('PAID', 'CANCELLED') GROUP BY i.merchant.id")
    java.util.List<Object[]> sumUnpaidByCustomerIds(@Param("customerIds") java.util.Collection<UUID> customerIds);

    // AI Phase 3 — cash flow feature queries

    @Query("SELECT i FROM Invoice i WHERE i.distributor.id = :distributorId " +
           "AND i.dueDate < :cutoffDate AND i.status NOT IN ('PAID', 'CANCELLED')")
    List<Invoice> findByDistributorIdAndDueDateBeforeAndPaidFalse(
            @Param("distributorId") UUID distributorId,
            @Param("cutoffDate") LocalDate cutoffDate);

    @Query("SELECT i FROM Invoice i WHERE i.distributor.id = :distributorId " +
           "AND i.dueDate >= :fromDate AND i.dueDate <= :toDate " +
           "AND i.status NOT IN ('PAID', 'CANCELLED')")
    List<Invoice> findByDistributorIdAndDueDateBetweenAndPaidFalse(
            @Param("distributorId") UUID distributorId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);

    @Query("SELECT i FROM Invoice i LEFT JOIN FETCH i.merchant WHERE i.distributor.id = :distributorId ORDER BY i.createdAt DESC")
    List<Invoice> findAllByDistributorIdForExport(@Param("distributorId") UUID distributorId);

    @Query("SELECT i FROM Invoice i LEFT JOIN FETCH i.merchant WHERE i.distributor.merchant.id = :merchantId ORDER BY i.createdAt DESC")
    List<Invoice> findAllByDistributorMerchantIdForExport(@Param("merchantId") UUID merchantId);

    @Query("SELECT i FROM Invoice i LEFT JOIN FETCH i.merchant ORDER BY i.createdAt DESC")
    List<Invoice> findAllForExport();

    // ── Payment stats aggregates ──────────────────────────────────────────────

    @Query("SELECT COALESCE(SUM(i.totalAmount), 0) FROM Invoice i WHERE i.distributor.id = :distributorId AND i.status NOT IN ('CANCELLED', 'DRAFT')")
    BigDecimal sumTotalAmountByDistributor(@Param("distributorId") UUID distributorId);

    @Query("SELECT COALESCE(SUM(i.paidAmount), 0) FROM Invoice i WHERE i.distributor.id = :distributorId AND i.status NOT IN ('CANCELLED', 'DRAFT')")
    BigDecimal sumPaidAmountByDistributor(@Param("distributorId") UUID distributorId);

    @Query("SELECT COALESCE(SUM(i.totalAmount - COALESCE(i.paidAmount, 0)), 0) FROM Invoice i WHERE i.distributor.id = :distributorId AND i.dueDate < CURRENT_DATE AND i.status NOT IN ('PAID', 'CANCELLED', 'DRAFT')")
    BigDecimal sumOverdueAmountByDistributor(@Param("distributorId") UUID distributorId);

    @Query("SELECT COUNT(i) FROM Invoice i WHERE i.distributor.id = :distributorId AND i.dueDate < CURRENT_DATE AND i.status NOT IN ('PAID', 'CANCELLED', 'DRAFT')")
    long countOverdueByDistributor(@Param("distributorId") UUID distributorId);

    @Query("SELECT COALESCE(SUM(i.totalAmount), 0) FROM Invoice i WHERE i.distributor.merchant.id = :merchantId AND i.status NOT IN ('CANCELLED', 'DRAFT')")
    BigDecimal sumTotalAmountByMerchant(@Param("merchantId") UUID merchantId);

    @Query("SELECT COALESCE(SUM(i.paidAmount), 0) FROM Invoice i WHERE i.distributor.merchant.id = :merchantId AND i.status NOT IN ('CANCELLED', 'DRAFT')")
    BigDecimal sumPaidAmountByMerchant(@Param("merchantId") UUID merchantId);

    @Query("SELECT COALESCE(SUM(i.totalAmount - COALESCE(i.paidAmount, 0)), 0) FROM Invoice i WHERE i.distributor.merchant.id = :merchantId AND i.dueDate < CURRENT_DATE AND i.status NOT IN ('PAID', 'CANCELLED', 'DRAFT')")
    BigDecimal sumOverdueAmountByMerchant(@Param("merchantId") UUID merchantId);

    @Query("SELECT COUNT(i) FROM Invoice i WHERE i.distributor.merchant.id = :merchantId AND i.dueDate < CURRENT_DATE AND i.status NOT IN ('PAID', 'CANCELLED', 'DRAFT')")
    long countOverdueByMerchant(@Param("merchantId") UUID merchantId);

    @Query("SELECT COALESCE(SUM(i.totalAmount), 0) FROM Invoice i WHERE i.status NOT IN ('CANCELLED', 'DRAFT')")
    BigDecimal sumTotalAmountAll();

    @Query("SELECT COALESCE(SUM(i.paidAmount), 0) FROM Invoice i WHERE i.status NOT IN ('CANCELLED', 'DRAFT')")
    BigDecimal sumPaidAmountAll();

    @Query("SELECT COALESCE(SUM(i.totalAmount - COALESCE(i.paidAmount, 0)), 0) FROM Invoice i WHERE i.dueDate < CURRENT_DATE AND i.status NOT IN ('PAID', 'CANCELLED', 'DRAFT')")
    BigDecimal sumOverdueAmountAll();

    @Query("SELECT COUNT(i) FROM Invoice i WHERE i.dueDate < CURRENT_DATE AND i.status NOT IN ('PAID', 'CANCELLED', 'DRAFT')")
    long countOverdueAll();
}
