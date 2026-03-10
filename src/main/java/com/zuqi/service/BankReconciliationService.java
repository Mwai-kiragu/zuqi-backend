package com.zuqi.service;

import com.zuqi.api.dto.accounting.BankReconciliationRequest;
import com.zuqi.api.dto.accounting.BankReconciliationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface BankReconciliationService {
    BankReconciliationResponse create(UUID distributorId, BankReconciliationRequest request);
    BankReconciliationResponse update(UUID id, BankReconciliationRequest request);
    BankReconciliationResponse reconcile(UUID id);
    BankReconciliationResponse getById(UUID id);
    Page<BankReconciliationResponse> getAll(UUID distributorId, Pageable pageable);
    void delete(UUID id);
}
