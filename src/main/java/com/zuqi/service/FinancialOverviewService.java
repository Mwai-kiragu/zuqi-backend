package com.zuqi.service;

import com.zuqi.api.dto.financial.FinancialOverviewResponse;

import java.time.LocalDate;

public interface FinancialOverviewService {

    FinancialOverviewResponse getOverview(LocalDate from, LocalDate to);
}
