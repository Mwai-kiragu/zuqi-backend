package com.zuqi.service;

import com.zuqi.api.dto.inventory.ProductBatchRequest;
import com.zuqi.api.dto.inventory.ProductBatchResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface ProductBatchService {
    ProductBatchResponse create(ProductBatchRequest request);
    ProductBatchResponse getById(UUID id);
    Page<ProductBatchResponse> getAll(Pageable pageable);
    List<ProductBatchResponse> getExpiringSoon(int daysAhead);
    ProductBatchResponse updateQuantity(UUID id, Double newQuantity);
}
