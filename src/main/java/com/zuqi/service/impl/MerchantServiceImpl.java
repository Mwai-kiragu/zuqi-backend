package com.zuqi.service.impl;

import com.zuqi.api.dto.merchant.MerchantRequest;
import com.zuqi.api.dto.merchant.MerchantResponse;
import com.zuqi.domain.merchant.Merchant;
import com.zuqi.exception.DuplicateResourceException;
import com.zuqi.exception.ResourceNotFoundException;
import com.zuqi.repository.DistributorRepository;
import com.zuqi.repository.MerchantRepository;
import com.zuqi.service.MerchantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MerchantServiceImpl implements MerchantService {

    private final MerchantRepository merchantRepository;
    private final DistributorRepository distributorRepository;

    private MerchantResponse toResponse(Merchant merchant) {
        MerchantResponse response = MerchantResponse.fromEntity(merchant);
        distributorRepository.findFirstByMerchantId(merchant.getId())
                .ifPresent(d -> response.setDistributorId(d.getId()));
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MerchantResponse> getAllMerchants(Boolean active, Pageable pageable) {
        if (active == null) {
            return merchantRepository.findAll(pageable).map(this::toResponse);
        }
        return merchantRepository.findByActive(active, pageable).map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public MerchantResponse getMerchantById(UUID id) {
        Merchant merchant = merchantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Merchant", "id", id.toString()));
        return toResponse(merchant);
    }

    @Override
    @Transactional
    public MerchantResponse createMerchant(MerchantRequest request) {
        if (merchantRepository.existsByName(request.getName())) {
            throw new DuplicateResourceException("Merchant", "name", request.getName());
        }
        if (request.getEmail() != null && merchantRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Merchant", "email", request.getEmail());
        }
        Merchant merchant = Merchant.builder()
                .name(request.getName())
                .registrationNumber(request.getRegistrationNumber())
                .email(request.getEmail())
                .phone(request.getPhone())
                .address(request.getAddress())
                .city(request.getCity())
                .country(request.getCountry() != null ? request.getCountry() : "Kenya")
                .logoUrl(request.getLogoUrl())
                .active(true)
                .build();
        Merchant saved = merchantRepository.save(merchant);
        log.info("Merchant brand created: {}", saved.getId());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public MerchantResponse updateMerchant(UUID id, MerchantRequest request) {
        Merchant merchant = merchantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Merchant", "id", id.toString()));
        if (!merchant.getName().equals(request.getName()) && merchantRepository.existsByName(request.getName())) {
            throw new DuplicateResourceException("Merchant", "name", request.getName());
        }
        merchant.setName(request.getName());
        merchant.setRegistrationNumber(request.getRegistrationNumber());
        merchant.setEmail(request.getEmail());
        merchant.setPhone(request.getPhone());
        merchant.setAddress(request.getAddress());
        merchant.setCity(request.getCity());
        if (request.getCountry() != null) merchant.setCountry(request.getCountry());
        merchant.setLogoUrl(request.getLogoUrl());
        return toResponse(merchantRepository.save(merchant));
    }

    @Override
    @Transactional
    public void deactivateMerchant(UUID id) {
        Merchant merchant = merchantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Merchant", "id", id.toString()));
        merchant.setActive(false);
        merchantRepository.save(merchant);
    }

    @Override
    @Transactional
    public void activateMerchant(UUID id) {
        Merchant merchant = merchantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Merchant", "id", id.toString()));
        merchant.setActive(true);
        merchantRepository.save(merchant);
    }
}
