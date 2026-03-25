package com.zuqi.repository;

import com.zuqi.domain.expense.Expense;
import com.zuqi.domain.expense.ExpenseCategory;
import com.zuqi.domain.expense.ExpenseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, UUID> {

    Page<Expense> findByDistributorIdOrderByExpenseDateDesc(UUID distributorId, Pageable pageable);

    Page<Expense> findByDistributorIdAndExpenseDateBetweenOrderByExpenseDateDesc(UUID distributorId, LocalDate from, LocalDate to, Pageable pageable);

    Page<Expense> findByDistributorIdAndStatusOrderByExpenseDateDesc(UUID distributorId, ExpenseStatus status, Pageable pageable);

    Page<Expense> findByDistributorIdAndStatusAndExpenseDateBetweenOrderByExpenseDateDesc(UUID distributorId, ExpenseStatus status, LocalDate from, LocalDate to, Pageable pageable);

    Page<Expense> findByExpenseDateBetweenOrderByExpenseDateDesc(LocalDate from, LocalDate to, Pageable pageable);

    Page<Expense> findByDistributorIdAndCategoryOrderByExpenseDateDesc(UUID distributorId, ExpenseCategory category, Pageable pageable);

    /** Scope to a merchant brand (MERCHANT_ADMIN). */
    @Query("SELECT e FROM Expense e WHERE e.distributorId IN " +
           "(SELECT d.id FROM Distributor d WHERE d.merchant.id = :merchantId) " +
           "ORDER BY e.expenseDate DESC")
    Page<Expense> findByDistributorMerchantId(@Param("merchantId") UUID merchantId, Pageable pageable);

    @Query("SELECT e FROM Expense e WHERE e.distributorId IN " +
           "(SELECT d.id FROM Distributor d WHERE d.merchant.id = :merchantId) " +
           "AND e.expenseDate BETWEEN :from AND :to " +
           "ORDER BY e.expenseDate DESC")
    Page<Expense> findByDistributorMerchantIdAndDateRange(
            @Param("merchantId") UUID merchantId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            Pageable pageable);

    @Query("SELECT e FROM Expense e WHERE e.distributorId IN " +
           "(SELECT d.id FROM Distributor d WHERE d.merchant.id = :merchantId) " +
           "AND e.status = :status " +
           "ORDER BY e.expenseDate DESC")
    Page<Expense> findByDistributorMerchantIdAndStatus(
            @Param("merchantId") UUID merchantId,
            @Param("status") ExpenseStatus status,
            Pageable pageable);

    @Query("SELECT e FROM Expense e WHERE e.distributorId IN " +
           "(SELECT d.id FROM Distributor d WHERE d.merchant.id = :merchantId) " +
           "AND e.status = :status AND e.expenseDate BETWEEN :from AND :to " +
           "ORDER BY e.expenseDate DESC")
    Page<Expense> findByDistributorMerchantIdAndStatusAndDateRange(
            @Param("merchantId") UUID merchantId,
            @Param("status") ExpenseStatus status,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            Pageable pageable);

    /** Sum of APPROVED + PAID expenses for a date range (used in Financial Overview). */
    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Expense e " +
           "WHERE e.distributorId = :distributorId " +
           "AND e.status IN ('APPROVED', 'PAID') " +
           "AND e.expenseDate BETWEEN :from AND :to")
    BigDecimal sumApprovedByDistributorAndDateRange(
            @Param("distributorId") UUID distributorId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /** Sum of APPROVED + PAID expenses for a merchant brand (MERCHANT_ADMIN). */
    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Expense e " +
           "WHERE e.distributorId IN (SELECT d.id FROM Distributor d WHERE d.merchant.id = :merchantId) " +
           "AND e.status IN ('APPROVED', 'PAID') " +
           "AND e.expenseDate BETWEEN :from AND :to")
    BigDecimal sumApprovedByMerchantAndDateRange(
            @Param("merchantId") UUID merchantId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /** AP balance: sum of APPROVED (unpaid) expenses for a distributor. */
    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Expense e " +
           "WHERE e.distributorId = :distributorId AND e.status = 'APPROVED'")
    BigDecimal sumApprovedUnpaidByDistributor(@Param("distributorId") UUID distributorId);

    /** AP balance for merchant brand. */
    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Expense e " +
           "WHERE e.distributorId IN (SELECT d.id FROM Distributor d WHERE d.merchant.id = :merchantId) " +
           "AND e.status = 'APPROVED'")
    BigDecimal sumApprovedUnpaidByMerchant(@Param("merchantId") UUID merchantId);

    /** Monthly expense totals — returns Object[]{year, month, sum} for last N months. */
    @Query("SELECT YEAR(e.expenseDate), MONTH(e.expenseDate), COALESCE(SUM(e.amount), 0) " +
           "FROM Expense e " +
           "WHERE e.distributorId = :distributorId " +
           "AND e.status IN ('APPROVED', 'PAID') " +
           "AND e.expenseDate >= :from " +
           "GROUP BY YEAR(e.expenseDate), MONTH(e.expenseDate)")
    java.util.List<Object[]> monthlyExpensesByDistributor(
            @Param("distributorId") UUID distributorId,
            @Param("from") LocalDate from);

    /** Monthly expense totals for merchant brand. */
    @Query("SELECT YEAR(e.expenseDate), MONTH(e.expenseDate), COALESCE(SUM(e.amount), 0) " +
           "FROM Expense e " +
           "WHERE e.distributorId IN (SELECT d.id FROM Distributor d WHERE d.merchant.id = :merchantId) " +
           "AND e.status IN ('APPROVED', 'PAID') " +
           "AND e.expenseDate >= :from " +
           "GROUP BY YEAR(e.expenseDate), MONTH(e.expenseDate)")
    java.util.List<Object[]> monthlyExpensesByMerchant(
            @Param("merchantId") UUID merchantId,
            @Param("from") LocalDate from);

    /** Category breakdown for a distributor. */
    @Query("SELECT e.category, COALESCE(SUM(e.amount), 0) FROM Expense e " +
           "WHERE e.distributorId = :distributorId " +
           "AND e.status IN ('APPROVED', 'PAID') " +
           "AND e.expenseDate BETWEEN :from AND :to " +
           "GROUP BY e.category")
    java.util.List<Object[]> categoryBreakdownByDistributor(
            @Param("distributorId") UUID distributorId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /** Category breakdown for a merchant brand. */
    @Query("SELECT e.category, COALESCE(SUM(e.amount), 0) FROM Expense e " +
           "WHERE e.distributorId IN (SELECT d.id FROM Distributor d WHERE d.merchant.id = :merchantId) " +
           "AND e.status IN ('APPROVED', 'PAID') " +
           "AND e.expenseDate BETWEEN :from AND :to " +
           "GROUP BY e.category")
    java.util.List<Object[]> categoryBreakdownByMerchant(
            @Param("merchantId") UUID merchantId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    // AI Phase 3 — cash flow feature queries
    @Query("SELECT e FROM Expense e WHERE e.distributorId = :distributorId " +
           "AND e.expenseDate >= :from AND e.expenseDate <= :to " +
           "AND e.status IN ('APPROVED', 'PAID')")
    java.util.List<Expense> findByDistributorIdAndDateBetween(
            @Param("distributorId") UUID distributorId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}
