package com.zuqi.service;

import com.zuqi.api.dto.returns.CreatePurchaseReturnRequest;
import com.zuqi.api.dto.returns.PurchaseReturnResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface PurchaseReturnService {
    PurchaseReturnResponse create(CreatePurchaseReturnRequest request, UUID createdById);
    PurchaseReturnResponse confirm(UUID id);
    PurchaseReturnResponse cancel(UUID id);
    PurchaseReturnResponse getById(UUID id);
    Page<PurchaseReturnResponse> getAll(Pageable pageable);
}
