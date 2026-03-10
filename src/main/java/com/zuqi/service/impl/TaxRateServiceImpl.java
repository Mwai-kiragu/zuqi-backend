package com.zuqi.service.impl;

import com.zuqi.api.dto.accounting.TaxRateRequest;
import com.zuqi.api.dto.accounting.TaxRateResponse;
import com.zuqi.domain.accounting.TaxRate;
import com.zuqi.exception.DuplicateResourceException;
import com.zuqi.exception.ResourceNotFoundException;
import com.zuqi.repository.TaxRateRepository;
import com.zuqi.service.TaxRateService;
import com.zuqi.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class TaxRateServiceImpl implements TaxRateService {

    private final TaxRateRepository taxRateRepository;
    private final SecurityUtils securityUtils;

    @Override
    public TaxRateResponse create(UUID distributorId, TaxRateRequest request) {
        if (taxRateRepository.existsByDistributorIdAndCode(distributorId, request.getCode().toUpperCase())) {
            throw new DuplicateResourceException("TaxRate", "code", request.getCode());
        }
        TaxRate taxRate = TaxRate.builder()
                .distributorId(distributorId)
                .name(request.getName())
                .code(request.getCode().toUpperCase())
                .rate(request.getRate())
                .taxType(request.getTaxType() != null ? request.getTaxType() : com.zuqi.domain.accounting.TaxType.PERCENTAGE)
                .appliesTo(request.getAppliesTo() != null ? request.getAppliesTo() : "ALL")
                .isCompound(request.isCompound())
                .isDefault(request.isDefault())
                .active(request.isActive())
                .description(request.getDescription())
                .effectiveFrom(request.getEffectiveFrom())
                .effectiveTo(request.getEffectiveTo())
                .createdBy(securityUtils.getCurrentUserId())
                .build();
        return TaxRateResponse.from(taxRateRepository.save(taxRate));
    }

    @Override
    public TaxRateResponse update(UUID id, TaxRateRequest request) {
        TaxRate taxRate = findById(id);
        // Check code uniqueness if changed
        String newCode = request.getCode().toUpperCase();
        if (!taxRate.getCode().equals(newCode)
                && taxRateRepository.existsByDistributorIdAndCode(taxRate.getDistributorId(), newCode)) {
            throw new DuplicateResourceException("TaxRate", "code", newCode);
        }
        taxRate.setName(request.getName());
        taxRate.setCode(newCode);
        taxRate.setRate(request.getRate());
        if (request.getTaxType() != null) taxRate.setTaxType(request.getTaxType());
        if (request.getAppliesTo() != null) taxRate.setAppliesTo(request.getAppliesTo());
        taxRate.setCompound(request.isCompound());
        taxRate.setDefault(request.isDefault());
        taxRate.setActive(request.isActive());
        taxRate.setDescription(request.getDescription());
        taxRate.setEffectiveFrom(request.getEffectiveFrom());
        taxRate.setEffectiveTo(request.getEffectiveTo());
        return TaxRateResponse.from(taxRateRepository.save(taxRate));
    }

    @Override
    @Transactional(readOnly = true)
    public TaxRateResponse getById(UUID id) {
        return TaxRateResponse.from(findById(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TaxRateResponse> getAll(UUID distributorId, Pageable pageable) {
        return taxRateRepository.findByDistributorIdOrderByNameAsc(distributorId, pageable)
                .map(TaxRateResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaxRateResponse> getActive(UUID distributorId) {
        return taxRateRepository.findByDistributorIdAndActiveOrderByNameAsc(distributorId, true)
                .stream().map(TaxRateResponse::from).collect(Collectors.toList());
    }

    @Override
    public void delete(UUID id) {
        TaxRate taxRate = findById(id);
        taxRateRepository.delete(taxRate);
    }

    private TaxRate findById(UUID id) {
        return taxRateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TaxRate", "id", id));
    }
}
