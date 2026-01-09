package com.zuqi.service;

import com.zuqi.api.dto.merchant.MerchantCategoryResponse;
import com.zuqi.api.dto.merchant.MerchantRequest;
import com.zuqi.api.dto.merchant.MerchantResponse;
import com.zuqi.domain.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface MerchantService {

    Page<MerchantResponse> getAllMerchants(Pageable pageable);

    Page<MerchantResponse> getInactiveMerchants(Pageable pageable);

    Page<MerchantResponse> getMerchantsByDistributor(UUID distributorId, Pageable pageable);

    Page<MerchantResponse> getInactiveMerchantsByDistributor(UUID distributorId, Pageable pageable);

    Page<MerchantResponse> getMerchantsBySalesRep(UUID salesRepId, Pageable pageable);

    Page<MerchantResponse> getMerchantsByCategory(Long categoryId, Pageable pageable);

    Page<MerchantResponse> searchMerchants(String searchTerm, UUID distributorId, Pageable pageable);

    MerchantResponse getMerchantById(UUID id);

    MerchantResponse createMerchant(MerchantRequest request);

    MerchantResponse updateMerchant(UUID id, MerchantRequest request);

    MerchantResponse assignSalesRep(UUID merchantId, UUID salesRepId);

    MerchantResponse verifyMerchant(UUID merchantId);

    void deactivateMerchant(UUID id, String reason, User currentUser);

    void activateMerchant(UUID id);

    List<MerchantCategoryResponse> getAllCategories();

    List<String> getDistinctCities();
}
