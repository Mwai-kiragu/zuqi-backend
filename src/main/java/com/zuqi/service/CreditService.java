package com.zuqi.service;

import com.zuqi.api.dto.credit.CreditLimitRequest;
import com.zuqi.api.dto.credit.CreditLimitResponse;
import com.zuqi.api.dto.credit.CreditScoreResponse;
import com.zuqi.domain.credit.CreditLimitStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface CreditService {

    Page<CreditLimitResponse> getCreditLimits(
            UUID distributorId,
            UUID merchantId,
            CreditLimitStatus status,
            Pageable pageable);

    Page<CreditLimitResponse> searchCreditLimits(UUID distributorId, String search, Pageable pageable);

    CreditLimitResponse getCreditLimitById(UUID id);

    CreditLimitResponse getActiveCreditLimit(UUID merchantId, UUID distributorId);

    CreditLimitResponse createCreditLimit(CreditLimitRequest request, UUID approvedById);

    CreditLimitResponse updateCreditLimit(UUID id, CreditLimitRequest request);

    CreditLimitResponse suspendCreditLimit(UUID id);

    CreditLimitResponse reactivateCreditLimit(UUID id);

    CreditScoreResponse getMerchantCreditScore(UUID merchantId);

    Page<CreditScoreResponse> getCreditScoreHistory(UUID merchantId, Pageable pageable);
}
