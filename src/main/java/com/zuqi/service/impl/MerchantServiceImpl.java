package com.zuqi.service.impl;

import com.zuqi.api.dto.merchant.MerchantCategoryResponse;
import com.zuqi.api.dto.merchant.MerchantRequest;
import com.zuqi.api.dto.merchant.MerchantResponse;
import com.zuqi.domain.distributor.Distributor;
import com.zuqi.domain.merchant.Merchant;
import com.zuqi.domain.merchant.MerchantCategory;
import com.zuqi.domain.user.User;
import com.zuqi.exception.DuplicateResourceException;
import com.zuqi.exception.ResourceNotFoundException;
import com.zuqi.repository.DistributorRepository;
import com.zuqi.repository.MerchantCategoryRepository;
import com.zuqi.repository.MerchantRepository;
import com.zuqi.repository.UserRepository;
import com.zuqi.service.MerchantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Implementation of the merchant service.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MerchantServiceImpl implements MerchantService {

    private final MerchantRepository merchantRepository;
    private final MerchantCategoryRepository categoryRepository;
    private final DistributorRepository distributorRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<MerchantResponse> getAllMerchants(Pageable pageable) {
        log.debug("Fetching all merchants");
        return merchantRepository.findByActiveTrue(pageable)
                .map(MerchantResponse::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MerchantResponse> getMerchantsByDistributor(UUID distributorId, Pageable pageable) {
        log.debug("Fetching merchants for distributor: {}", distributorId);
        return merchantRepository.findByDistributorIdAndActiveTrue(distributorId, pageable)
                .map(MerchantResponse::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MerchantResponse> getMerchantsBySalesRep(UUID salesRepId, Pageable pageable) {
        log.debug("Fetching merchants for sales rep: {}", salesRepId);
        return merchantRepository.findByAssignedSalesRepIdAndActiveTrue(salesRepId, pageable)
                .map(MerchantResponse::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MerchantResponse> getMerchantsByCategory(Long categoryId, Pageable pageable) {
        log.debug("Fetching merchants for category: {}", categoryId);
        return merchantRepository.findByCategoryIdAndActiveTrue(categoryId, pageable)
                .map(MerchantResponse::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MerchantResponse> searchMerchants(String searchTerm, UUID distributorId, Pageable pageable) {
        log.debug("Searching merchants with term: {}, distributor: {}", searchTerm, distributorId);
        if (distributorId != null) {
            return merchantRepository.searchByDistributor(distributorId, searchTerm, pageable)
                    .map(MerchantResponse::fromEntity);
        }
        return merchantRepository.searchByBusinessName(searchTerm, pageable)
                .map(MerchantResponse::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public MerchantResponse getMerchantById(UUID id) {
        log.debug("Fetching merchant by ID: {}", id);
        Merchant merchant = merchantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Merchant", "id", id.toString()));
        return MerchantResponse.fromEntity(merchant);
    }

    @Override
    @Transactional
    public MerchantResponse createMerchant(MerchantRequest request) {
        log.info("Creating new merchant: {}", request.getBusinessName());

        // Check for duplicate phone
        if (merchantRepository.existsByPhone(request.getPhone())) {
            throw new DuplicateResourceException("Merchant", "phone", request.getPhone());
        }

        // Check for duplicate email if provided
        if (request.getEmail() != null && merchantRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Merchant", "email", request.getEmail());
        }

        Merchant merchant = Merchant.builder()
                .businessName(request.getBusinessName())
                .ownerName(request.getOwnerName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .address(request.getAddress())
                .city(request.getCity())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .creditLimit(request.getCreditLimit())
                .paymentTermsDays(request.getPaymentTermsDays() != null ? request.getPaymentTermsDays() : 0)
                .build();

        // Set category if provided
        if (request.getCategoryId() != null) {
            MerchantCategory category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("MerchantCategory", "id", request.getCategoryId().toString()));
            merchant.setCategory(category);
        }

        // Set distributor if provided
        if (request.getDistributorId() != null) {
            Distributor distributor = distributorRepository.findById(request.getDistributorId())
                    .orElseThrow(() -> new ResourceNotFoundException("Distributor", "id", request.getDistributorId().toString()));
            merchant.setDistributor(distributor);
        }

        // Set sales rep if provided
        if (request.getAssignedSalesRepId() != null) {
            User salesRep = userRepository.findById(request.getAssignedSalesRepId())
                    .orElseThrow(() -> new ResourceNotFoundException("User", "id", request.getAssignedSalesRepId().toString()));
            merchant.setAssignedSalesRep(salesRep);
        }

        Merchant savedMerchant = merchantRepository.save(merchant);
        log.info("Merchant created successfully with ID: {}", savedMerchant.getId());

        return MerchantResponse.fromEntity(savedMerchant);
    }

    @Override
    @Transactional
    public MerchantResponse updateMerchant(UUID id, MerchantRequest request) {
        log.info("Updating merchant: {}", id);

        Merchant merchant = merchantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Merchant", "id", id.toString()));

        // Check for duplicate phone if changed
        if (!merchant.getPhone().equals(request.getPhone()) && merchantRepository.existsByPhone(request.getPhone())) {
            throw new DuplicateResourceException("Merchant", "phone", request.getPhone());
        }

        // Check for duplicate email if changed
        if (request.getEmail() != null && !request.getEmail().equals(merchant.getEmail())
                && merchantRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Merchant", "email", request.getEmail());
        }

        merchant.setBusinessName(request.getBusinessName());
        merchant.setOwnerName(request.getOwnerName());
        merchant.setEmail(request.getEmail());
        merchant.setPhone(request.getPhone());
        merchant.setAddress(request.getAddress());
        merchant.setCity(request.getCity());
        merchant.setLatitude(request.getLatitude());
        merchant.setLongitude(request.getLongitude());

        if (request.getCreditLimit() != null) {
            merchant.setCreditLimit(request.getCreditLimit());
        }
        if (request.getPaymentTermsDays() != null) {
            merchant.setPaymentTermsDays(request.getPaymentTermsDays());
        }

        // Update category if provided
        if (request.getCategoryId() != null) {
            MerchantCategory category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("MerchantCategory", "id", request.getCategoryId().toString()));
            merchant.setCategory(category);
        }

        // Update distributor if provided
        if (request.getDistributorId() != null) {
            Distributor distributor = distributorRepository.findById(request.getDistributorId())
                    .orElseThrow(() -> new ResourceNotFoundException("Distributor", "id", request.getDistributorId().toString()));
            merchant.setDistributor(distributor);
        }

        // Update sales rep if provided
        if (request.getAssignedSalesRepId() != null) {
            User salesRep = userRepository.findById(request.getAssignedSalesRepId())
                    .orElseThrow(() -> new ResourceNotFoundException("User", "id", request.getAssignedSalesRepId().toString()));
            merchant.setAssignedSalesRep(salesRep);
        }

        Merchant updatedMerchant = merchantRepository.save(merchant);
        log.info("Merchant updated successfully: {}", id);

        return MerchantResponse.fromEntity(updatedMerchant);
    }

    @Override
    @Transactional
    public MerchantResponse assignSalesRep(UUID merchantId, UUID salesRepId) {
        log.info("Assigning sales rep {} to merchant {}", salesRepId, merchantId);

        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Merchant", "id", merchantId.toString()));

        User salesRep = userRepository.findById(salesRepId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", salesRepId.toString()));

        merchant.setAssignedSalesRep(salesRep);
        Merchant updatedMerchant = merchantRepository.save(merchant);

        log.info("Sales rep assigned successfully");
        return MerchantResponse.fromEntity(updatedMerchant);
    }

    @Override
    @Transactional
    public MerchantResponse verifyMerchant(UUID merchantId) {
        log.info("Verifying merchant: {}", merchantId);

        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Merchant", "id", merchantId.toString()));

        merchant.setVerified(true);
        Merchant updatedMerchant = merchantRepository.save(merchant);

        log.info("Merchant verified successfully");
        return MerchantResponse.fromEntity(updatedMerchant);
    }

    @Override
    @Transactional
    public void deactivateMerchant(UUID id) {
        log.info("Deactivating merchant: {}", id);

        Merchant merchant = merchantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Merchant", "id", id.toString()));

        merchant.setActive(false);
        merchantRepository.save(merchant);

        log.info("Merchant deactivated successfully");
    }

    @Override
    @Transactional(readOnly = true)
    public List<MerchantCategoryResponse> getAllCategories() {
        log.debug("Fetching all merchant categories");
        return categoryRepository.findAll().stream()
                .map(MerchantCategoryResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getDistinctCities() {
        log.debug("Fetching distinct cities");
        return merchantRepository.findDistinctCities();
    }
}
