package com.zuqi.service;

import com.zuqi.api.dto.merchant.MerchantCategoryResponse;
import com.zuqi.api.dto.merchant.MerchantRequest;
import com.zuqi.api.dto.merchant.MerchantResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

/**
 * Service interface for merchant operations.
 */
public interface MerchantService {

    /**
     * Get all merchants with pagination.
     */
    Page<MerchantResponse> getAllMerchants(Pageable pageable);

    /**
     * Get merchants by distributor with pagination.
     */
    Page<MerchantResponse> getMerchantsByDistributor(UUID distributorId, Pageable pageable);

    /**
     * Get merchants by assigned sales rep.
     */
    Page<MerchantResponse> getMerchantsBySalesRep(UUID salesRepId, Pageable pageable);

    /**
     * Get merchants by category.
     */
    Page<MerchantResponse> getMerchantsByCategory(Long categoryId, Pageable pageable);

    /**
     * Search merchants.
     */
    Page<MerchantResponse> searchMerchants(String searchTerm, UUID distributorId, Pageable pageable);

    /**
     * Get a merchant by ID.
     */
    MerchantResponse getMerchantById(UUID id);

    /**
     * Create a new merchant.
     */
    MerchantResponse createMerchant(MerchantRequest request);

    /**
     * Update an existing merchant.
     */
    MerchantResponse updateMerchant(UUID id, MerchantRequest request);

    /**
     * Assign a sales rep to a merchant.
     */
    MerchantResponse assignSalesRep(UUID merchantId, UUID salesRepId);

    /**
     * Verify a merchant.
     */
    MerchantResponse verifyMerchant(UUID merchantId);

    /**
     * Deactivate a merchant.
     */
    void deactivateMerchant(UUID id);

    /**
     * Get all merchant categories.
     */
    List<MerchantCategoryResponse> getAllCategories();

    /**
     * Get distinct cities for filtering.
     */
    List<String> getDistinctCities();
}
