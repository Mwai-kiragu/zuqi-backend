package com.zuqi.service;

import com.zuqi.api.dto.pricing.CreatePriceListRequest;
import com.zuqi.api.dto.pricing.PriceListResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface PriceListService {
    PriceListResponse create(CreatePriceListRequest request);
    PriceListResponse update(UUID id, CreatePriceListRequest request);
    PriceListResponse getById(UUID id);
    Page<PriceListResponse> getAll(Pageable pageable);
    Page<PriceListResponse.ItemResponse> getItems(UUID priceListId, String search, Pageable pageable);
    void delete(UUID id);
}
