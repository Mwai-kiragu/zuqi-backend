package com.zuqi.service;

import com.zuqi.api.dto.expense.ExpenseRequest;
import com.zuqi.api.dto.expense.ExpenseResponse;
import com.zuqi.domain.expense.ExpenseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.UUID;

public interface ExpenseService {

    Page<ExpenseResponse> getAll(ExpenseStatus status, LocalDate startDate, LocalDate endDate, Pageable pageable);

    ExpenseResponse getById(UUID id);

    ExpenseResponse create(UUID distributorId, ExpenseRequest request);

    ExpenseResponse update(UUID id, ExpenseRequest request);

    ExpenseResponse submit(UUID id);

    ExpenseResponse approve(UUID id);

    ExpenseResponse reject(UUID id, String reason);

    ExpenseResponse markPaid(UUID id);

    void delete(UUID id);
}
