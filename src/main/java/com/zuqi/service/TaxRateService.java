package com.zuqi.service;

import com.zuqi.api.dto.accounting.TaxRateRequest;
import com.zuqi.api.dto.accounting.TaxRateResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface TaxRateService {
    TaxRateResponse create(UUID distributorId, TaxRateRequest request);
    TaxRateResponse update(UUID id, TaxRateRequest request);
    TaxRateResponse getById(UUID id);
    Page<TaxRateResponse> getAll(UUID distributorId, Pageable pageable);
    List<TaxRateResponse> getActive(UUID distributorId);
    void delete(UUID id);
}
