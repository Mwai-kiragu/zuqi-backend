package com.zuqi.service;

import com.zuqi.api.dto.returns.CreateSalesReturnRequest;
import com.zuqi.api.dto.returns.SalesReturnResponse;
import com.zuqi.api.dto.returns.SalesReturnStatsResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface SalesReturnService {
    SalesReturnResponse create(CreateSalesReturnRequest request, UUID createdById);
    SalesReturnResponse confirm(UUID id);
    SalesReturnResponse cancel(UUID id);
    SalesReturnResponse getById(UUID id);
    Page<SalesReturnResponse> getAll(Pageable pageable);
    SalesReturnStatsResponse getStats();
    List<SalesReturnResponse> getAllForExport();
}
