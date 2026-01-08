package com.zuqi.service;

import com.zuqi.api.dto.credit.CreditLimitRequest;
import com.zuqi.api.dto.credit.CreditLimitResponse;
import com.zuqi.api.dto.credit.CreditScoreResponse;
import com.zuqi.domain.credit.CreditLimitStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Service interface for credit management operations.
 */
public interface CreditService {

    /**
     * Get all credit limits for a distributor.
     */
    Page<CreditLimitResponse> getCreditLimits(
            UUID distributorId,
            UUID merchantId,
            CreditLimitStatus status,
            Pageable pageable);

    /**
     * Search credit limits.
     */
    Page<CreditLimitResponse> searchCreditLimits(UUID distributorId, String search, Pageable pageable);

    /**
     * Get a credit limit by ID.
     */
    CreditLimitResponse getCreditLimitById(UUID id);

    /**
     * Get the active credit limit for a merchant.
     */
    CreditLimitResponse getActiveCreditLimit(UUID merchantId, UUID distributorId);

    /**
     * Create a new credit limit.
     */
    CreditLimitResponse createCreditLimit(CreditLimitRequest request, UUID approvedById);

    /**
     * Update an existing credit limit.
     */
    CreditLimitResponse updateCreditLimit(UUID id, CreditLimitRequest request);

    /**
     * Suspend a credit limit.
     */
    CreditLimitResponse suspendCreditLimit(UUID id);

    /**
     * Reactivate a credit limit.
     */
    CreditLimitResponse reactivateCreditLimit(UUID id);

    /**
     * Get the latest credit score for a merchant.
     */
    CreditScoreResponse getMerchantCreditScore(UUID merchantId);

    /**
     * Get credit score history for a merchant.
     */
    Page<CreditScoreResponse> getCreditScoreHistory(UUID merchantId, Pageable pageable);
}
