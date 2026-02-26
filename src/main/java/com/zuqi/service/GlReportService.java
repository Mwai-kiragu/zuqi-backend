package com.zuqi.service;

import com.zuqi.api.dto.gl.BudgetVarianceResponse;
import com.zuqi.api.dto.gl.TrialBalanceResponse;

import java.util.UUID;

public interface GlReportService {
    TrialBalanceResponse getTrialBalance(UUID distributorId, UUID periodId);
    BudgetVarianceResponse getBudgetVariance(UUID distributorId, int year, Integer month);
}
