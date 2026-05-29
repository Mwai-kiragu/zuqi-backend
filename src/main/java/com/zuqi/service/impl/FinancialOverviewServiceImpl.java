package com.zuqi.service.impl;

import com.zuqi.api.dto.financial.FinancialOverviewResponse;
import com.zuqi.api.dto.financial.FinancialOverviewResponse.CategoryData;
import com.zuqi.api.dto.financial.FinancialOverviewResponse.MonthlyData;
import com.zuqi.domain.gl.GlAccount;
import com.zuqi.domain.gl.SystemAccountType;
import com.zuqi.repository.DistributorRepository;
import com.zuqi.repository.ExpenseRepository;
import com.zuqi.repository.GlAccountRepository;
import com.zuqi.repository.InvoiceRepository;
import com.zuqi.repository.JournalEntryLineRepository;
import com.zuqi.service.FinancialOverviewService;
import com.zuqi.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FinancialOverviewServiceImpl implements FinancialOverviewService {

    private final InvoiceRepository invoiceRepository;
    private final ExpenseRepository expenseRepository;
    private final GlAccountRepository glAccountRepository;
    private final JournalEntryLineRepository journalEntryLineRepository;
    private final DistributorRepository distributorRepository;
    private final SecurityUtils securityUtils;

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("MMM yyyy");

    @Override
    public FinancialOverviewResponse getOverview(LocalDate from, LocalDate to) {
        UUID merchantId = securityUtils.getCurrentUserMerchantId();
        UUID distributorId = merchantId == null ? securityUtils.getDistributorIdForFiltering() : null;

        BigDecimal revenue = getRevenue(merchantId, distributorId, from, to);
        BigDecimal expenses = getExpenses(merchantId, distributorId, from, to);
        BigDecimal netIncome = revenue.subtract(expenses);
        BigDecimal profitMargin = revenue.compareTo(BigDecimal.ZERO) > 0
                ? netIncome.divide(revenue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        BigDecimal arBalance = getArBalance(merchantId, distributorId);
        BigDecimal apBalance = getApBalance(merchantId, distributorId);
        BigDecimal cashPosition = getCashPosition(merchantId, distributorId, to);

        LocalDate sixMonthsAgo = to.minusMonths(5).withDayOfMonth(1);
        List<MonthlyData> monthly = buildMonthlyBreakdown(merchantId, distributorId, sixMonthsAgo, to);
        List<CategoryData> byCategory = buildCategoryBreakdown(merchantId, distributorId, from, to, expenses);

        return FinancialOverviewResponse.builder()
                .totalRevenue(revenue)
                .totalExpenses(expenses)
                .netIncome(netIncome)
                .profitMarginPct(profitMargin.setScale(2, RoundingMode.HALF_UP))
                .arBalance(arBalance)
                .apBalance(apBalance)
                .cashPosition(cashPosition)
                .monthlyBreakdown(monthly)
                .expenseByCategory(byCategory)
                .fromDate(from.toString())
                .toDate(to.toString())
                .build();
    }

    // ── Revenue ─────────────────────────────────────────────────────────────

    private BigDecimal getRevenue(UUID merchantId, UUID distributorId, LocalDate from, LocalDate to) {
        if (merchantId != null) {
            return coalesce(invoiceRepository.sumPaidByMerchantAndDateRange(merchantId, from, to));
        }
        if (distributorId != null) {
            return coalesce(invoiceRepository.sumPaidByDistributorAndDateRange(distributorId, from, to));
        }
        // SUPER_ADMIN — aggregate all (not feasible to sum all in one call without a dedicated query; return zero)
        return BigDecimal.ZERO;
    }

    // ── Expenses ─────────────────────────────────────────────────────────────

    private BigDecimal getExpenses(UUID merchantId, UUID distributorId, LocalDate from, LocalDate to) {
        if (merchantId != null) {
            return coalesce(expenseRepository.sumApprovedByMerchantAndDateRange(merchantId, from, to));
        }
        if (distributorId != null) {
            return coalesce(expenseRepository.sumApprovedByDistributorAndDateRange(distributorId, from, to));
        }
        return BigDecimal.ZERO;
    }

    // ── AR Balance ───────────────────────────────────────────────────────────

    private BigDecimal getArBalance(UUID merchantId, UUID distributorId) {
        if (merchantId != null) {
            return coalesce(invoiceRepository.sumArBalanceByMerchant(merchantId));
        }
        if (distributorId != null) {
            return coalesce(invoiceRepository.sumArBalanceByDistributor(distributorId));
        }
        return BigDecimal.ZERO;
    }

    // ── AP Balance ───────────────────────────────────────────────────────────

    private BigDecimal getApBalance(UUID merchantId, UUID distributorId) {
        if (merchantId != null) {
            return coalesce(expenseRepository.sumApprovedUnpaidByMerchant(merchantId));
        }
        if (distributorId != null) {
            return coalesce(expenseRepository.sumApprovedUnpaidByDistributor(distributorId));
        }
        return BigDecimal.ZERO;
    }

    // ── Cash Position ────────────────────────────────────────────────────────

    private BigDecimal getCashPosition(UUID merchantId, UUID distributorId, LocalDate asOf) {
        if (merchantId != null) {
            // Sum cash positions across all active distributors of this merchant
            return distributorRepository.findByMerchantIdAndActiveTrue(merchantId).stream()
                    .map(d -> cashPositionForDistributor(d.getId(), asOf))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        if (distributorId == null) return BigDecimal.ZERO;
        return cashPositionForDistributor(distributorId, asOf);
    }

    private BigDecimal cashPositionForDistributor(UUID distId, LocalDate asOf) {
        Optional<GlAccount> cashAcct = glAccountRepository
                .findByDistributorIdAndSystemAccountType(distId, SystemAccountType.CASH_AND_BANK);
        if (cashAcct.isEmpty()) return BigDecimal.ZERO;

        UUID accountId = cashAcct.get().getId();
        var lines = journalEntryLineRepository.findPostedLinesUpToDate(distId, asOf);
        BigDecimal balance = BigDecimal.ZERO;
        for (var line : lines) {
            if (line.getAccount().getId().equals(accountId)) {
                balance = balance.add(line.getDebitAmount()).subtract(line.getCreditAmount());
            }
        }
        return balance;
    }

    // ── Monthly Breakdown ────────────────────────────────────────────────────

    private List<MonthlyData> buildMonthlyBreakdown(UUID merchantId, UUID distributorId,
                                                     LocalDate from, LocalDate to) {
        List<Object[]> revenueRows;
        List<Object[]> expenseRows;

        if (merchantId != null) {
            revenueRows = invoiceRepository.monthlyRevenueByMerchant(merchantId, from);
            expenseRows = expenseRepository.monthlyExpensesByMerchant(merchantId, from);
        } else if (distributorId != null) {
            revenueRows = invoiceRepository.monthlyRevenueByDistributor(distributorId, from);
            expenseRows = expenseRepository.monthlyExpensesByDistributor(distributorId, from);
        } else {
            return Collections.emptyList();
        }

        // Key: year * 100 + month
        Map<Integer, BigDecimal> revenueMap = toMap(revenueRows);
        Map<Integer, BigDecimal> expenseMap = toMap(expenseRows);

        List<MonthlyData> result = new ArrayList<>();
        LocalDate cursor = from;
        while (!cursor.isAfter(to)) {
            int key = cursor.getYear() * 100 + cursor.getMonthValue();
            result.add(MonthlyData.builder()
                    .year(cursor.getYear())
                    .month(cursor.getMonthValue())
                    .monthLabel(cursor.format(MONTH_FMT))
                    .revenue(revenueMap.getOrDefault(key, BigDecimal.ZERO))
                    .expenses(expenseMap.getOrDefault(key, BigDecimal.ZERO))
                    .build());
            cursor = cursor.plusMonths(1);
        }
        return result;
    }

    private Map<Integer, BigDecimal> toMap(List<Object[]> rows) {
        Map<Integer, BigDecimal> map = new HashMap<>();
        for (Object[] row : rows) {
            int year = ((Number) row[0]).intValue();
            int month = ((Number) row[1]).intValue();
            BigDecimal amount = row[2] instanceof BigDecimal bd ? bd : BigDecimal.valueOf(((Number) row[2]).doubleValue());
            map.put(year * 100 + month, amount);
        }
        return map;
    }

    // ── Category Breakdown ───────────────────────────────────────────────────

    private List<CategoryData> buildCategoryBreakdown(UUID merchantId, UUID distributorId,
                                                       LocalDate from, LocalDate to,
                                                       BigDecimal totalExpenses) {
        List<Object[]> rows;
        if (merchantId != null) {
            rows = expenseRepository.categoryBreakdownByMerchant(merchantId, from, to);
        } else if (distributorId != null) {
            rows = expenseRepository.categoryBreakdownByDistributor(distributorId, from, to);
        } else {
            return Collections.emptyList();
        }

        List<CategoryData> result = new ArrayList<>();
        for (Object[] row : rows) {
            String category = row[0].toString();
            BigDecimal amount = row[1] instanceof BigDecimal bd ? bd : BigDecimal.valueOf(((Number) row[1]).doubleValue());
            BigDecimal pct = totalExpenses.compareTo(BigDecimal.ZERO) > 0
                    ? amount.divide(totalExpenses, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                    : BigDecimal.ZERO;
            result.add(CategoryData.builder()
                    .category(category)
                    .amount(amount)
                    .percentage(pct.setScale(2, RoundingMode.HALF_UP))
                    .build());
        }
        return result;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private BigDecimal coalesce(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
