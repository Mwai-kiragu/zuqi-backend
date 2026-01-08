package com.zuqi.service.impl;

import com.zuqi.api.dto.credit.CreditLimitRequest;
import com.zuqi.api.dto.credit.CreditLimitResponse;
import com.zuqi.api.dto.credit.CreditScoreResponse;
import com.zuqi.domain.credit.CreditLimit;
import com.zuqi.domain.credit.CreditLimitStatus;
import com.zuqi.domain.credit.CreditScore;
import com.zuqi.exception.ResourceNotFoundException;
import com.zuqi.repository.*;
import com.zuqi.service.CreditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Implementation of CreditService.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CreditServiceImpl implements CreditService {

    private final CreditLimitRepository creditLimitRepository;
    private final CreditScoreRepository creditScoreRepository;
    private final MerchantRepository merchantRepository;
    private final DistributorRepository distributorRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<CreditLimitResponse> getCreditLimits(
            UUID distributorId,
            UUID merchantId,
            CreditLimitStatus status,
            Pageable pageable) {
        log.debug("Getting credit limits for distributor: {}", distributorId);

        return creditLimitRepository.findByFilters(distributorId, merchantId, status, pageable)
                .map(this::mapToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CreditLimitResponse> searchCreditLimits(UUID distributorId, String search, Pageable pageable) {
        log.debug("Searching credit limits for distributor: {}, search: {}", distributorId, search);

        return creditLimitRepository.searchCreditLimits(distributorId, search, pageable)
                .map(this::mapToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public CreditLimitResponse getCreditLimitById(UUID id) {
        log.debug("Getting credit limit by ID: {}", id);

        CreditLimit creditLimit = creditLimitRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CreditLimit", "id", id));

        return mapToResponse(creditLimit);
    }

    @Override
    @Transactional(readOnly = true)
    public CreditLimitResponse getActiveCreditLimit(UUID merchantId, UUID distributorId) {
        log.debug("Getting active credit limit for merchant: {} distributor: {}", merchantId, distributorId);

        return creditLimitRepository.findByMerchantIdAndDistributorIdAndStatus(
                        merchantId, distributorId, CreditLimitStatus.ACTIVE)
                .map(this::mapToResponse)
                .orElse(null);
    }

    @Override
    public CreditLimitResponse createCreditLimit(CreditLimitRequest request, UUID approvedById) {
        log.debug("Creating credit limit for merchant: {}", request.getMerchantId());

        var merchant = merchantRepository.findById(request.getMerchantId())
                .orElseThrow(() -> new ResourceNotFoundException("Merchant", "id", request.getMerchantId()));

        var distributor = distributorRepository.findById(request.getDistributorId())
                .orElseThrow(() -> new ResourceNotFoundException("Distributor", "id", request.getDistributorId()));

        var approvedBy = userRepository.findById(approvedById)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", approvedById));

        CreditLimit creditLimit = CreditLimit.builder()
                .merchant(merchant)
                .distributor(distributor)
                .approvedLimit(request.getApprovedLimit())
                .availableLimit(request.getApprovedLimit())
                .interestRate(request.getInterestRate())
                .expiresAt(request.getExpiresAt())
                .approvedBy(approvedBy)
                .approvedAt(LocalDateTime.now())
                .status(CreditLimitStatus.ACTIVE)
                .build();

        creditLimit = creditLimitRepository.save(creditLimit);
        log.info("Created credit limit with ID: {} for merchant: {}", creditLimit.getId(), merchant.getId());

        return mapToResponse(creditLimit);
    }

    @Override
    public CreditLimitResponse updateCreditLimit(UUID id, CreditLimitRequest request) {
        log.debug("Updating credit limit: {}", id);

        CreditLimit creditLimit = creditLimitRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CreditLimit", "id", id));

        creditLimit.setApprovedLimit(request.getApprovedLimit());
        creditLimit.setAvailableLimit(request.getApprovedLimit().subtract(creditLimit.getUtilizedAmount()));
        creditLimit.setInterestRate(request.getInterestRate());
        creditLimit.setExpiresAt(request.getExpiresAt());

        creditLimit = creditLimitRepository.save(creditLimit);
        log.info("Updated credit limit: {}", id);

        return mapToResponse(creditLimit);
    }

    @Override
    public CreditLimitResponse suspendCreditLimit(UUID id) {
        log.debug("Suspending credit limit: {}", id);

        CreditLimit creditLimit = creditLimitRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CreditLimit", "id", id));

        creditLimit.setStatus(CreditLimitStatus.SUSPENDED);
        creditLimit = creditLimitRepository.save(creditLimit);
        log.info("Suspended credit limit: {}", id);

        return mapToResponse(creditLimit);
    }

    @Override
    public CreditLimitResponse reactivateCreditLimit(UUID id) {
        log.debug("Reactivating credit limit: {}", id);

        CreditLimit creditLimit = creditLimitRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CreditLimit", "id", id));

        creditLimit.setStatus(CreditLimitStatus.ACTIVE);
        creditLimit = creditLimitRepository.save(creditLimit);
        log.info("Reactivated credit limit: {}", id);

        return mapToResponse(creditLimit);
    }

    @Override
    @Transactional(readOnly = true)
    public CreditScoreResponse getMerchantCreditScore(UUID merchantId) {
        log.debug("Getting credit score for merchant: {}", merchantId);

        return creditScoreRepository.findLatestByMerchantId(merchantId)
                .map(this::mapToScoreResponse)
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CreditScoreResponse> getCreditScoreHistory(UUID merchantId, Pageable pageable) {
        log.debug("Getting credit score history for merchant: {}", merchantId);

        return creditScoreRepository.findByMerchantId(merchantId, pageable)
                .map(this::mapToScoreResponse);
    }

    private CreditLimitResponse mapToResponse(CreditLimit creditLimit) {
        return CreditLimitResponse.builder()
                .id(creditLimit.getId())
                .merchantId(creditLimit.getMerchant().getId())
                .merchantName(creditLimit.getMerchant().getBusinessName())
                .merchantPhone(creditLimit.getMerchant().getPhone())
                .distributorId(creditLimit.getDistributor().getId())
                .distributorName(creditLimit.getDistributor().getName())
                .approvedLimit(creditLimit.getApprovedLimit())
                .utilizedAmount(creditLimit.getUtilizedAmount())
                .availableLimit(creditLimit.getAvailableLimit())
                .interestRate(creditLimit.getInterestRate())
                .status(creditLimit.getStatus().name())
                .approvedById(creditLimit.getApprovedBy() != null ? creditLimit.getApprovedBy().getId() : null)
                .approvedByName(creditLimit.getApprovedBy() != null ?
                        creditLimit.getApprovedBy().getFirstName() + " " + creditLimit.getApprovedBy().getLastName() : null)
                .approvedAt(creditLimit.getApprovedAt())
                .expiresAt(creditLimit.getExpiresAt())
                .createdAt(creditLimit.getCreatedAt())
                .updatedAt(creditLimit.getUpdatedAt())
                .build();
    }

    private CreditScoreResponse mapToScoreResponse(CreditScore score) {
        return CreditScoreResponse.builder()
                .id(score.getId())
                .merchantId(score.getMerchant().getId())
                .merchantName(score.getMerchant().getBusinessName())
                .score(score.getScore())
                .scoreGrade(score.getScoreGrade())
                .factors(score.getFactors())
                .modelVersion(score.getModelVersion())
                .validUntil(score.getValidUntil())
                .createdAt(score.getCreatedAt())
                .build();
    }
}
