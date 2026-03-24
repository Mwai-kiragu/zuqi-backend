package com.zuqi.service;

import com.zuqi.api.dto.pricing.CreatePromotionRequest;
import com.zuqi.api.dto.pricing.PromotionResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface PromotionService {
    PromotionResponse create(CreatePromotionRequest request);
    PromotionResponse update(UUID id, CreatePromotionRequest request);
    PromotionResponse getById(UUID id);
    Page<PromotionResponse> getAll(Pageable pageable);
    void delete(UUID id);
}
