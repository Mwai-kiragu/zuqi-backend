package com.zuqi.service;

import com.zuqi.api.dto.merchant.MerchantRequest;
import com.zuqi.api.dto.merchant.MerchantResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface MerchantService {

    Page<MerchantResponse> getAllMerchants(Boolean active, Pageable pageable);

    MerchantResponse getMerchantById(UUID id);

    MerchantResponse createMerchant(MerchantRequest request);

    MerchantResponse updateMerchant(UUID id, MerchantRequest request);

    void deactivateMerchant(UUID id);

    void activateMerchant(UUID id);
}
