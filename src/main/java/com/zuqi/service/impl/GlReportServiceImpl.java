package com.zuqi.service.impl;

import com.zuqi.api.dto.gl.*;
import com.zuqi.domain.gl.*;
import com.zuqi.exception.ResourceNotFoundException;
import com.zuqi.repository.*;
import com.zuqi.service.GlReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class GlReportServiceImpl implements GlReportService {

    private final GlPeriodRepository glPeriodRepository;
    private final GlAccountRepository glAccountRepository;
    private final JournalEntryLineRepository journalEntryLineRepository;
    private final BudgetRepository budgetRepository;

    @Override
    public TrialBalanceResponse getTrialBalance(UUID distributorId, UUID periodId) {
        GlPeriod period = glPeriodRepository.findById(periodId)
                .orElseThrow(() -> new ResourceNotFoundException("GlPeriod", "id", periodId));

        List<JournalEntryLine> lines = journalEntryLineRepository.findPostedLinesForPeriod(distributorId, periodId);

        Map<UUID, BigDecimal[]> accountTotals = new LinkedHashMap<>();
        for (JournalEntryLine line : lines) {
            UUID accountId = line.getAccount().getId();
            accountTotals.computeIfAbsent(accountId, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            accountTotals.get(accountId)[0] = accountTotals.get(accountId)[0].add(line.getDebitAmount());
            accountTotals.get(accountId)[1] = accountTotals.get(accountId)[1].add(line.getCreditAmount());
        }

        List<GlAccount> accounts = glAccountRepository.findByDistributorIdOrderByAccountCodeAsc(distributorId);
        List<TrialBalanceRow> rows = new ArrayList<>();
        BigDecimal totalDebits = BigDecimal.ZERO;
        BigDecimal totalCredits = BigDecimal.ZERO;

        for (GlAccount account : accounts) {
            if (!account.isPostingAccount()) continue;
            BigDecimal[] totals = accountTotals.getOrDefault(account.getId(), new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            BigDecimal dr = totals[0];
            BigDecimal cr = totals[1];

            if (dr.compareTo(BigDecimal.ZERO) == 0 && cr.compareTo(BigDecimal.ZERO) == 0) continue;

            BigDecimal closingDr = dr.compareTo(cr) > 0 ? dr.subtract(cr) : BigDecimal.ZERO;
            BigDecimal closingCr = cr.compareTo(dr) > 0 ? cr.subtract(dr) : BigDecimal.ZERO;

            rows.add(TrialBalanceRow.builder()
                    .accountId(account.getId())
                    .accountCode(account.getAccountCode())
                    .accountName(account.getAccountName())
                    .accountType(account.getAccountType())
                    .normalBalance(account.getNormalBalance())
                    .periodDebit(dr)
                    .periodCredit(cr)
                    .closingDebit(closingDr)
                    .closingCredit(closingCr)
                    .build());

            totalDebits = totalDebits.add(dr);
            totalCredits = totalCredits.add(cr);
        }

        return TrialBalanceResponse.builder()
                .periodName(period.getPeriodName())
                .asOfDate(period.getEndDate())
                .rows(rows)
                .totalDebits(totalDebits)
                .totalCredits(totalCredits)
                .build();
    }

    @Override
    public BudgetVarianceResponse getBudgetVariance(UUID distributorId, int year, Integer month) {
        List<Budget> budgets = month != null
                ? budgetRepository.findByDistributorIdAndBudgetYearAndPeriodMonthOrderByAccountIdAsc(distributorId, year, month)
                : budgetRepository.findByDistributorIdAndBudgetYearOrderByPeriodMonthAsc(distributorId, year);

        List<TrialBalanceRow> allActuals = new ArrayList<>();
        Set<Integer> months = budgets.stream().map(Budget::getPeriodMonth).collect(Collectors.toSet());
        for (int m : months) {
            glPeriodRepository.findByDistributorIdAndPeriodYearAndPeriodMonth(distributorId, year, m)
                    .ifPresent(p -> {
                        TrialBalanceResponse tb = getTrialBalance(distributorId, p.getId());
                        allActuals.addAll(tb.getRows());
                    });
        }

        Map<String, BigDecimal> actuals = new HashMap<>();
        for (TrialBalanceRow row : allActuals) {
            String key = row.getAccountId() + "-" + row.getAccountCode();
            actuals.merge(key, row.getPeriodDebit().subtract(row.getPeriodCredit()), BigDecimal::add);
        }

        List<BudgetVarianceRow> rows = budgets.stream().map(b -> {
            String key = b.getAccount().getId() + "-" + b.getAccount().getAccountCode();
            BigDecimal actual = actuals.getOrDefault(key, BigDecimal.ZERO);
            BigDecimal variance = b.getBudgetedAmount().subtract(actual);
            BigDecimal variancePct = b.getBudgetedAmount().compareTo(BigDecimal.ZERO) != 0
                    ? variance.divide(b.getBudgetedAmount(), 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                    : BigDecimal.ZERO;
            return BudgetVarianceRow.builder()
                    .accountId(b.getAccount().getId())
                    .accountCode(b.getAccount().getAccountCode())
                    .accountName(b.getAccount().getAccountName())
                    .periodMonth(b.getPeriodMonth())
                    .budgetedAmount(b.getBudgetedAmount())
                    .actualAmount(actual)
                    .variance(variance)
                    .variancePct(variancePct)
                    .build();
        }).collect(Collectors.toList());

        return BudgetVarianceResponse.builder()
                .budgetYear(year)
                .periodMonth(month)
                .rows(rows)
                .build();
    }
}
