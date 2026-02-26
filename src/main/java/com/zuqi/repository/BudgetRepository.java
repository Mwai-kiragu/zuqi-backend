package com.zuqi.repository;

import com.zuqi.domain.gl.Budget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BudgetRepository extends JpaRepository<Budget, UUID> {

    List<Budget> findByDistributorIdAndBudgetYearOrderByPeriodMonthAsc(UUID distributorId, int budgetYear);

    List<Budget> findByDistributorIdAndBudgetYearAndPeriodMonthOrderByAccountIdAsc(
            UUID distributorId, int budgetYear, int periodMonth);

    Optional<Budget> findByDistributorIdAndBudgetYearAndPeriodMonthAndAccountIdAndCostCenterId(
            UUID distributorId, int budgetYear, int periodMonth, UUID accountId, UUID costCenterId);

    void deleteByDistributorIdAndBudgetYearAndPeriodMonthAndAccountId(
            UUID distributorId, int budgetYear, int periodMonth, UUID accountId);
}
