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
import java.math.RoundingMode;
import java.time.LocalDate;
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

        // Opening balances: all posted activity strictly before period start
        List<JournalEntryLine> openingLines = journalEntryLineRepository.findPostedLinesBeforeDate(distributorId, period.getStartDate());
        Map<UUID, BigDecimal[]> openingTotals = new LinkedHashMap<>();
        for (JournalEntryLine line : openingLines) {
            UUID accountId = line.getAccount().getId();
            openingTotals.computeIfAbsent(accountId, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            openingTotals.get(accountId)[0] = openingTotals.get(accountId)[0].add(line.getDebitAmount());
            openingTotals.get(accountId)[1] = openingTotals.get(accountId)[1].add(line.getCreditAmount());
        }

        // Period activity
        List<JournalEntryLine> lines = journalEntryLineRepository.findPostedLinesForPeriod(distributorId, periodId);
        Map<UUID, BigDecimal[]> periodTotals = new LinkedHashMap<>();
        for (JournalEntryLine line : lines) {
            UUID accountId = line.getAccount().getId();
            periodTotals.computeIfAbsent(accountId, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            periodTotals.get(accountId)[0] = periodTotals.get(accountId)[0].add(line.getDebitAmount());
            periodTotals.get(accountId)[1] = periodTotals.get(accountId)[1].add(line.getCreditAmount());
        }

        List<GlAccount> accounts = glAccountRepository.findByDistributorIdOrderByAccountCodeAsc(distributorId);
        List<TrialBalanceRow> rows = new ArrayList<>();
        BigDecimal totalDebits = BigDecimal.ZERO;
        BigDecimal totalCredits = BigDecimal.ZERO;

        for (GlAccount account : accounts) {
            if (!account.isPostingAccount()) continue;
            BigDecimal[] opening = openingTotals.getOrDefault(account.getId(), new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            BigDecimal[] perTotals = periodTotals.getOrDefault(account.getId(), new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});

            BigDecimal openDr = opening[0];
            BigDecimal openCr = opening[1];
            BigDecimal perDr = perTotals[0];
            BigDecimal perCr = perTotals[1];

            if (openDr.compareTo(BigDecimal.ZERO) == 0 && openCr.compareTo(BigDecimal.ZERO) == 0
                    && perDr.compareTo(BigDecimal.ZERO) == 0 && perCr.compareTo(BigDecimal.ZERO) == 0) continue;

            BigDecimal totalDr = openDr.add(perDr);
            BigDecimal totalCr = openCr.add(perCr);
            BigDecimal closingDr = totalDr.compareTo(totalCr) > 0 ? totalDr.subtract(totalCr) : BigDecimal.ZERO;
            BigDecimal closingCr = totalCr.compareTo(totalDr) > 0 ? totalCr.subtract(totalDr) : BigDecimal.ZERO;

            BigDecimal openingNetDr = openDr.compareTo(openCr) > 0 ? openDr.subtract(openCr) : BigDecimal.ZERO;
            BigDecimal openingNetCr = openCr.compareTo(openDr) > 0 ? openCr.subtract(openDr) : BigDecimal.ZERO;

            rows.add(TrialBalanceRow.builder()
                    .accountId(account.getId())
                    .accountCode(account.getAccountCode())
                    .accountName(account.getAccountName())
                    .accountType(account.getAccountType())
                    .normalBalance(account.getNormalBalance())
                    .openingDebit(openingNetDr)
                    .openingCredit(openingNetCr)
                    .periodDebit(perDr)
                    .periodCredit(perCr)
                    .closingDebit(closingDr)
                    .closingCredit(closingCr)
                    .build());

            totalDebits = totalDebits.add(closingDr);
            totalCredits = totalCredits.add(closingCr);
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
    public TrialBalanceResponse getTrialBalanceByRange(UUID distributorId, LocalDate fromDate, LocalDate toDate) {
        // Opening balances: all activity strictly before fromDate
        List<JournalEntryLine> openingLines = journalEntryLineRepository.findPostedLinesBeforeDate(distributorId, fromDate);
        Map<UUID, BigDecimal[]> openingTotals = new LinkedHashMap<>();
        for (JournalEntryLine line : openingLines) {
            UUID accountId = line.getAccount().getId();
            openingTotals.computeIfAbsent(accountId, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            openingTotals.get(accountId)[0] = openingTotals.get(accountId)[0].add(line.getDebitAmount());
            openingTotals.get(accountId)[1] = openingTotals.get(accountId)[1].add(line.getCreditAmount());
        }

        // Period activity: fromDate to toDate inclusive
        List<JournalEntryLine> periodLines = journalEntryLineRepository.findPostedLinesForDateRange(distributorId, fromDate, toDate);
        Map<UUID, BigDecimal[]> periodTotals = new LinkedHashMap<>();
        for (JournalEntryLine line : periodLines) {
            UUID accountId = line.getAccount().getId();
            periodTotals.computeIfAbsent(accountId, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            periodTotals.get(accountId)[0] = periodTotals.get(accountId)[0].add(line.getDebitAmount());
            periodTotals.get(accountId)[1] = periodTotals.get(accountId)[1].add(line.getCreditAmount());
        }

        List<GlAccount> accounts = glAccountRepository.findByDistributorIdOrderByAccountCodeAsc(distributorId);
        List<TrialBalanceRow> rows = new ArrayList<>();
        BigDecimal totalDebits = BigDecimal.ZERO;
        BigDecimal totalCredits = BigDecimal.ZERO;

        for (GlAccount account : accounts) {
            if (!account.isPostingAccount()) continue;
            BigDecimal[] opening = openingTotals.getOrDefault(account.getId(), new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            BigDecimal[] period = periodTotals.getOrDefault(account.getId(), new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});

            BigDecimal openDr = opening[0];
            BigDecimal openCr = opening[1];
            BigDecimal perDr = period[0];
            BigDecimal perCr = period[1];

            if (openDr.compareTo(BigDecimal.ZERO) == 0 && openCr.compareTo(BigDecimal.ZERO) == 0
                    && perDr.compareTo(BigDecimal.ZERO) == 0 && perCr.compareTo(BigDecimal.ZERO) == 0) continue;

            BigDecimal totalDr = openDr.add(perDr);
            BigDecimal totalCr = openCr.add(perCr);
            BigDecimal closingDr = totalDr.compareTo(totalCr) > 0 ? totalDr.subtract(totalCr) : BigDecimal.ZERO;
            BigDecimal closingCr = totalCr.compareTo(totalDr) > 0 ? totalCr.subtract(totalDr) : BigDecimal.ZERO;

            BigDecimal openingNetDr = openDr.compareTo(openCr) > 0 ? openDr.subtract(openCr) : BigDecimal.ZERO;
            BigDecimal openingNetCr = openCr.compareTo(openDr) > 0 ? openCr.subtract(openDr) : BigDecimal.ZERO;

            rows.add(TrialBalanceRow.builder()
                    .accountId(account.getId())
                    .accountCode(account.getAccountCode())
                    .accountName(account.getAccountName())
                    .accountType(account.getAccountType())
                    .normalBalance(account.getNormalBalance())
                    .openingDebit(openingNetDr)
                    .openingCredit(openingNetCr)
                    .periodDebit(perDr)
                    .periodCredit(perCr)
                    .closingDebit(closingDr)
                    .closingCredit(closingCr)
                    .build());

            totalDebits = totalDebits.add(closingDr);
            totalCredits = totalCredits.add(closingCr);
        }

        String rangeLabel = fromDate + " to " + toDate;
        return TrialBalanceResponse.builder()
                .periodName(rangeLabel)
                .asOfDate(toDate)
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
                    .accountType(b.getAccount().getAccountType())
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

    @Override
    public GeneralLedgerResponse getGeneralLedger(UUID distributorId, LocalDate fromDate, LocalDate toDate) {
        List<GlAccount> accounts = glAccountRepository.findByDistributorIdAndActiveOrderByAccountCodeAsc(distributorId, true);

        // Opening balances: all posted lines before fromDate
        List<JournalEntryLine> openingLines = journalEntryLineRepository.findPostedLinesBeforeDate(distributorId, fromDate);
        Map<UUID, BigDecimal> openingBalances = new HashMap<>();
        for (JournalEntryLine line : openingLines) {
            UUID acctId = line.getAccount().getId();
            BigDecimal net = line.getDebitAmount().subtract(line.getCreditAmount());
            openingBalances.merge(acctId, net, BigDecimal::add);
        }

        // Period lines grouped by account
        List<JournalEntryLine> periodLines = journalEntryLineRepository.findPostedLinesForDateRange(distributorId, fromDate, toDate);
        Map<UUID, List<JournalEntryLine>> linesByAccount = new LinkedHashMap<>();
        for (JournalEntryLine line : periodLines) {
            linesByAccount.computeIfAbsent(line.getAccount().getId(), k -> new ArrayList<>()).add(line);
        }

        List<GeneralLedgerAccountRow> accountRows = new ArrayList<>();
        for (GlAccount account : accounts) {
            if (!account.isPostingAccount()) continue;
            List<JournalEntryLine> acctLines = linesByAccount.get(account.getId());
            BigDecimal openingBal = openingBalances.getOrDefault(account.getId(), BigDecimal.ZERO);
            // Adjust opening balance sign for CREDIT normal balance accounts
            if (account.getNormalBalance() == NormalBalance.CREDIT) {
                openingBal = openingBal.negate();
            }
            if (acctLines == null && openingBal.compareTo(BigDecimal.ZERO) == 0) continue;

            BigDecimal running = openingBal;
            BigDecimal totalDr = BigDecimal.ZERO;
            BigDecimal totalCr = BigDecimal.ZERO;
            List<GeneralLedgerLine> glLines = new ArrayList<>();

            if (acctLines != null) {
                for (JournalEntryLine line : acctLines) {
                    BigDecimal dr = line.getDebitAmount();
                    BigDecimal cr = line.getCreditAmount();
                    if (account.getNormalBalance() == NormalBalance.DEBIT) {
                        running = running.add(dr).subtract(cr);
                    } else {
                        running = running.subtract(dr).add(cr);
                    }
                    totalDr = totalDr.add(dr);
                    totalCr = totalCr.add(cr);
                    glLines.add(GeneralLedgerLine.builder()
                            .date(line.getJournalEntry().getEntryDate())
                            .entryNumber(line.getJournalEntry().getEntryNumber())
                            .description(line.getDescription() != null ? line.getDescription() : line.getJournalEntry().getDescription())
                            .reference(line.getReference())
                            .debit(dr)
                            .credit(cr)
                            .runningBalance(running)
                            .build());
                }
            }

            accountRows.add(GeneralLedgerAccountRow.builder()
                    .accountId(account.getId())
                    .accountCode(account.getAccountCode())
                    .accountName(account.getAccountName())
                    .accountType(account.getAccountType())
                    .openingBalance(openingBal)
                    .totalDebit(totalDr)
                    .totalCredit(totalCr)
                    .closingBalance(running)
                    .lines(glLines)
                    .build());
        }

        return GeneralLedgerResponse.builder()
                .fromDate(fromDate)
                .toDate(toDate)
                .accounts(accountRows)
                .build();
    }

    @Override
    public BalanceSheetResponse getBalanceSheet(UUID distributorId, LocalDate asOfDate) {
        List<JournalEntryLine> lines = journalEntryLineRepository.findPostedLinesUpToDate(distributorId, asOfDate);

        Map<UUID, BigDecimal[]> accountTotals = new LinkedHashMap<>();
        for (JournalEntryLine line : lines) {
            UUID acctId = line.getAccount().getId();
            accountTotals.computeIfAbsent(acctId, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            accountTotals.get(acctId)[0] = accountTotals.get(acctId)[0].add(line.getDebitAmount());
            accountTotals.get(acctId)[1] = accountTotals.get(acctId)[1].add(line.getCreditAmount());
        }

        List<GlAccount> accounts = glAccountRepository.findByDistributorIdOrderByAccountCodeAsc(distributorId);

        List<BalanceSheetRow> assetRows = new ArrayList<>();
        List<BalanceSheetRow> liabilityRows = new ArrayList<>();
        List<BalanceSheetRow> equityRows = new ArrayList<>();
        BigDecimal totalAssets = BigDecimal.ZERO;
        BigDecimal totalLiabilities = BigDecimal.ZERO;
        BigDecimal totalEquity = BigDecimal.ZERO;

        for (GlAccount account : accounts) {
            if (!account.isPostingAccount()) continue;
            AccountType type = account.getAccountType();
            if (type == AccountType.REVENUE || type == AccountType.EXPENSE) continue;

            BigDecimal[] totals = accountTotals.getOrDefault(account.getId(), new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            BigDecimal dr = totals[0];
            BigDecimal cr = totals[1];
            // Balance: ASSET/EXPENSE normal=DEBIT → balance = debit - credit; LIABILITY/EQUITY/REVENUE normal=CREDIT → credit - debit
            BigDecimal balance = account.getNormalBalance() == NormalBalance.DEBIT
                    ? dr.subtract(cr) : cr.subtract(dr);
            if (balance.compareTo(BigDecimal.ZERO) == 0) continue;

            BalanceSheetRow row = BalanceSheetRow.builder()
                    .accountId(account.getId())
                    .accountCode(account.getAccountCode())
                    .accountName(account.getAccountName())
                    .balance(balance)
                    .build();

            if (type == AccountType.ASSET) {
                assetRows.add(row);
                totalAssets = totalAssets.add(balance);
            } else if (type == AccountType.LIABILITY) {
                liabilityRows.add(row);
                totalLiabilities = totalLiabilities.add(balance);
            } else if (type == AccountType.EQUITY) {
                equityRows.add(row);
                totalEquity = totalEquity.add(balance);
            }
        }

        return BalanceSheetResponse.builder()
                .asOfDate(asOfDate)
                .assets(BalanceSheetSection.builder().name("Assets").rows(assetRows).total(totalAssets).build())
                .liabilities(BalanceSheetSection.builder().name("Liabilities").rows(liabilityRows).total(totalLiabilities).build())
                .equity(BalanceSheetSection.builder().name("Equity").rows(equityRows).total(totalEquity).build())
                .totalLiabilitiesAndEquity(totalLiabilities.add(totalEquity))
                .build();
    }

    @Override
    public ProfitLossResponse getProfitAndLoss(UUID distributorId, LocalDate fromDate, LocalDate toDate) {
        List<JournalEntryLine> lines = journalEntryLineRepository.findPostedLinesForDateRange(distributorId, fromDate, toDate);

        Map<UUID, BigDecimal[]> accountTotals = new LinkedHashMap<>();
        for (JournalEntryLine line : lines) {
            UUID acctId = line.getAccount().getId();
            accountTotals.computeIfAbsent(acctId, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            accountTotals.get(acctId)[0] = accountTotals.get(acctId)[0].add(line.getDebitAmount());
            accountTotals.get(acctId)[1] = accountTotals.get(acctId)[1].add(line.getCreditAmount());
        }

        List<GlAccount> accounts = glAccountRepository.findByDistributorIdOrderByAccountCodeAsc(distributorId);

        List<ProfitLossRow> revenueRows = new ArrayList<>();
        List<ProfitLossRow> cogsRows = new ArrayList<>();
        List<ProfitLossRow> expenseRows = new ArrayList<>();
        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal totalCogs = BigDecimal.ZERO;
        BigDecimal totalExpenses = BigDecimal.ZERO;

        for (GlAccount account : accounts) {
            if (!account.isPostingAccount()) continue;
            AccountType type = account.getAccountType();
            if (type != AccountType.REVENUE && type != AccountType.EXPENSE) continue;

            BigDecimal[] totals = accountTotals.getOrDefault(account.getId(), new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            BigDecimal dr = totals[0];
            BigDecimal cr = totals[1];
            // REVENUE: normal=CREDIT → amount = cr - dr; EXPENSE: normal=DEBIT → amount = dr - cr
            BigDecimal amount = account.getNormalBalance() == NormalBalance.CREDIT
                    ? cr.subtract(dr) : dr.subtract(cr);
            if (amount.compareTo(BigDecimal.ZERO) == 0) continue;

            ProfitLossRow row = ProfitLossRow.builder()
                    .accountId(account.getId())
                    .accountCode(account.getAccountCode())
                    .accountName(account.getAccountName())
                    .amount(amount)
                    .build();

            if (type == AccountType.REVENUE) {
                revenueRows.add(row);
                totalRevenue = totalRevenue.add(amount);
            } else {
                // COGS or Operating Expense
                if (account.getAccountSubType() == AccountSubType.COGS) {
                    cogsRows.add(row);
                    totalCogs = totalCogs.add(amount);
                } else {
                    expenseRows.add(row);
                    totalExpenses = totalExpenses.add(amount);
                }
            }
        }

        BigDecimal grossProfit = totalRevenue.subtract(totalCogs);
        BigDecimal netIncome = grossProfit.subtract(totalExpenses);

        return ProfitLossResponse.builder()
                .fromDate(fromDate)
                .toDate(toDate)
                .revenue(ProfitLossSection.builder().name("Revenue").rows(revenueRows).total(totalRevenue).build())
                .costOfGoods(ProfitLossSection.builder().name("Cost of Goods Sold").rows(cogsRows).total(totalCogs).build())
                .expenses(ProfitLossSection.builder().name("Operating Expenses").rows(expenseRows).total(totalExpenses).build())
                .grossProfit(grossProfit)
                .netIncome(netIncome)
                .build();
    }

    @Override
    public CashFlowResponse getCashFlowStatement(UUID distributorId, LocalDate fromDate, LocalDate toDate) {
        // Compute account balances at period start (before fromDate) and end (up to toDate)
        Map<UUID, BigDecimal[]> openingTotals = buildAccountTotals(
                journalEntryLineRepository.findPostedLinesBeforeDate(distributorId, fromDate));
        Map<UUID, BigDecimal[]> closingTotals = buildAccountTotals(
                journalEntryLineRepository.findPostedLinesUpToDate(distributorId, toDate));

        List<GlAccount> accounts = glAccountRepository.findByDistributorIdOrderByAccountCodeAsc(distributorId);

        // Get net income from P&L
        ProfitLossResponse pl = getProfitAndLoss(distributorId, fromDate, toDate);
        BigDecimal netIncome = pl.getNetIncome();

        List<CashFlowRow> operatingRows = new ArrayList<>();
        List<CashFlowRow> investingRows = new ArrayList<>();
        List<CashFlowRow> financingRows = new ArrayList<>();

        operatingRows.add(CashFlowRow.builder().label("Net Income").amount(netIncome).build());

        BigDecimal totalOperating = netIncome;
        BigDecimal totalInvesting = BigDecimal.ZERO;
        BigDecimal totalFinancing = BigDecimal.ZERO;

        for (GlAccount account : accounts) {
            if (!account.isPostingAccount()) continue;
            AccountType type = account.getAccountType();
            AccountSubType subType = account.getAccountSubType();
            if (type == AccountType.REVENUE || type == AccountType.EXPENSE) continue;

            BigDecimal openBal = computeBalance(openingTotals.getOrDefault(account.getId(),
                    new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO}), account.getNormalBalance());
            BigDecimal closeBal = computeBalance(closingTotals.getOrDefault(account.getId(),
                    new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO}), account.getNormalBalance());
            BigDecimal change = closeBal.subtract(openBal);
            if (change.compareTo(BigDecimal.ZERO) == 0) continue;

            if (type == AccountType.ASSET && subType == AccountSubType.CURRENT_ASSET) {
                // Increase in current asset = use of cash (negative operating)
                BigDecimal cashEffect = change.negate();
                operatingRows.add(CashFlowRow.builder().label("Change in " + account.getAccountName()).amount(cashEffect).build());
                totalOperating = totalOperating.add(cashEffect);
            } else if (type == AccountType.LIABILITY && subType == AccountSubType.CURRENT_LIABILITY) {
                // Increase in current liability = source of cash (positive operating)
                operatingRows.add(CashFlowRow.builder().label("Change in " + account.getAccountName()).amount(change).build());
                totalOperating = totalOperating.add(change);
            } else if (type == AccountType.ASSET && subType == AccountSubType.FIXED_ASSET) {
                // Increase in fixed assets = investing outflow (negative)
                BigDecimal cashEffect = change.negate();
                investingRows.add(CashFlowRow.builder().label(account.getAccountName()).amount(cashEffect).build());
                totalInvesting = totalInvesting.add(cashEffect);
            } else if (type == AccountType.LIABILITY && subType == AccountSubType.LONG_TERM_LIABILITY) {
                // Increase in long-term debt = financing inflow
                financingRows.add(CashFlowRow.builder().label(account.getAccountName()).amount(change).build());
                totalFinancing = totalFinancing.add(change);
            } else if (type == AccountType.EQUITY && subType != AccountSubType.RETAINED_EARNINGS) {
                // Equity contributions / withdrawals
                financingRows.add(CashFlowRow.builder().label(account.getAccountName()).amount(change).build());
                totalFinancing = totalFinancing.add(change);
            }
        }

        BigDecimal netCashChange = totalOperating.add(totalInvesting).add(totalFinancing);

        return CashFlowResponse.builder()
                .fromDate(fromDate)
                .toDate(toDate)
                .operatingActivities(CashFlowSection.builder().name("Operating Activities").rows(operatingRows).total(totalOperating).build())
                .investingActivities(CashFlowSection.builder().name("Investing Activities").rows(investingRows).total(totalInvesting).build())
                .financingActivities(CashFlowSection.builder().name("Financing Activities").rows(financingRows).total(totalFinancing).build())
                .netCashChange(netCashChange)
                .build();
    }

    // ─── helpers ────────────────────────────────────────────────────────────────

    private Map<UUID, BigDecimal[]> buildAccountTotals(List<JournalEntryLine> lines) {
        Map<UUID, BigDecimal[]> totals = new HashMap<>();
        for (JournalEntryLine line : lines) {
            UUID acctId = line.getAccount().getId();
            totals.computeIfAbsent(acctId, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            totals.get(acctId)[0] = totals.get(acctId)[0].add(line.getDebitAmount());
            totals.get(acctId)[1] = totals.get(acctId)[1].add(line.getCreditAmount());
        }
        return totals;
    }

    private BigDecimal computeBalance(BigDecimal[] drCr, NormalBalance normalBalance) {
        return normalBalance == NormalBalance.DEBIT
                ? drCr[0].subtract(drCr[1])
                : drCr[1].subtract(drCr[0]);
    }
}
