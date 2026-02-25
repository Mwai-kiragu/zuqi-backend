package com.zuqi.service;

import com.zuqi.api.dto.gl.BudgetBulkRequest;
import com.zuqi.api.dto.gl.BudgetResponse;
import com.zuqi.domain.user.User;

import java.util.List;
import java.util.UUID;

public interface BudgetService {
    List<BudgetResponse> getByYearAndMonth(UUID distributorId, int year, int month);
    List<BudgetResponse> getByYear(UUID distributorId, int year);
    List<BudgetResponse> upsert(UUID distributorId, BudgetBulkRequest request, User currentUser);
}
