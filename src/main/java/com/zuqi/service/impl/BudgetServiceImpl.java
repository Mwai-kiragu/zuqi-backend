package com.zuqi.service.impl;

import com.zuqi.api.dto.gl.BudgetBulkRequest;
import com.zuqi.api.dto.gl.BudgetRequest;
import com.zuqi.api.dto.gl.BudgetResponse;
import com.zuqi.domain.gl.Budget;
import com.zuqi.domain.gl.CostCenter;
import com.zuqi.domain.gl.GlAccount;
import com.zuqi.domain.user.User;
import com.zuqi.exception.ResourceNotFoundException;
import com.zuqi.repository.BudgetRepository;
import com.zuqi.repository.CostCenterRepository;
import com.zuqi.repository.GlAccountRepository;
import com.zuqi.service.BudgetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class BudgetServiceImpl implements BudgetService {

    private final BudgetRepository budgetRepository;
    private final GlAccountRepository glAccountRepository;
    private final CostCenterRepository costCenterRepository;

    @Override
    public List<BudgetResponse> getByYearAndMonth(UUID distributorId, int year, int month) {
        return budgetRepository.findByDistributorIdAndBudgetYearAndPeriodMonthOrderByAccountIdAsc(distributorId, year, month)
                .stream().map(BudgetResponse::fromEntity).collect(Collectors.toList());
    }

    @Override
    public List<BudgetResponse> getByYear(UUID distributorId, int year) {
        return budgetRepository.findByDistributorIdAndBudgetYearOrderByPeriodMonthAsc(distributorId, year)
                .stream().map(BudgetResponse::fromEntity).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public List<BudgetResponse> upsert(UUID distributorId, BudgetBulkRequest request, User currentUser) {
        return request.getEntries().stream().map(entry -> upsertSingle(distributorId, request.getBudgetYear(),
                request.getPeriodMonth(), entry, currentUser)).collect(Collectors.toList());
    }

    private BudgetResponse upsertSingle(UUID distributorId, int year, int month,
                                         BudgetRequest req, User currentUser) {
        GlAccount account = glAccountRepository.findById(req.getAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("GlAccount", "id", req.getAccountId()));

        CostCenter costCenter = null;
        if (req.getCostCenterId() != null) {
            costCenter = costCenterRepository.findById(req.getCostCenterId())
                    .orElseThrow(() -> new ResourceNotFoundException("CostCenter", "id", req.getCostCenterId()));
        }

        final CostCenter finalCostCenter = costCenter;
        final UUID ccId = costCenter != null ? costCenter.getId() : null;

        Optional<Budget> existing = budgetRepository
                .findByDistributorIdAndBudgetYearAndPeriodMonthAndAccountIdAndCostCenterId(
                        distributorId, year, month, account.getId(), ccId);

        Budget budget;
        if (existing.isPresent()) {
            budget = existing.get();
            budget.setBudgetedAmount(req.getBudgetedAmount());
            budget.setNotes(req.getNotes());
        } else {
            budget = Budget.builder()
                    .distributorId(distributorId)
                    .budgetYear(year)
                    .periodMonth(month)
                    .account(account)
                    .costCenter(finalCostCenter)
                    .budgetedAmount(req.getBudgetedAmount())
                    .notes(req.getNotes())
                    .createdBy(currentUser.getId())
                    .build();
        }
        return BudgetResponse.fromEntity(budgetRepository.save(budget));
    }
}
