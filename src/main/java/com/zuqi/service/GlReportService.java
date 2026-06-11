package com.zuqi.service;

import com.zuqi.api.dto.gl.*;

import java.time.LocalDate;
import java.util.UUID;

public interface GlReportService {
    TrialBalanceResponse getTrialBalance(UUID distributorId, UUID periodId);
    TrialBalanceResponse getTrialBalanceByRange(UUID distributorId, LocalDate fromDate, LocalDate toDate);
    BudgetVarianceResponse getBudgetVariance(UUID distributorId, int year, Integer month);
    GeneralLedgerResponse getGeneralLedger(UUID distributorId, LocalDate fromDate, LocalDate toDate);
    BalanceSheetResponse getBalanceSheet(UUID distributorId, LocalDate asOfDate);
    ProfitLossResponse getProfitAndLoss(UUID distributorId, LocalDate fromDate, LocalDate toDate);
    CashFlowResponse getCashFlowStatement(UUID distributorId, LocalDate fromDate, LocalDate toDate);
}
