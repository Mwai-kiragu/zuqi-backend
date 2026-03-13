package com.zuqi.service.impl;

import com.zuqi.api.dto.supplier.SupplierCategoryRequest;
import com.zuqi.api.dto.supplier.SupplierCategoryResponse;
import com.zuqi.api.dto.supplier.SupplierRequest;
import com.zuqi.api.dto.supplier.SupplierResponse;
import com.zuqi.domain.distributor.Distributor;
import com.zuqi.domain.supplier.Supplier;
import com.zuqi.domain.supplier.SupplierCategory;
import com.zuqi.domain.user.User;
import com.zuqi.exception.DuplicateResourceException;
import com.zuqi.exception.ResourceNotFoundException;
import com.zuqi.repository.DistributorRepository;
import com.zuqi.repository.SupplierCategoryRepository;
import com.zuqi.repository.SupplierRepository;
import com.zuqi.service.SupplierService;
import com.zuqi.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SupplierServiceImpl implements SupplierService {

    private final SupplierRepository supplierRepository;
    private final SupplierCategoryRepository categoryRepository;
    private final DistributorRepository distributorRepository;
    private final SecurityUtils securityUtils;

    private String generateSupplierCode() {
        long count = supplierRepository.countAll();
        return String.format("SUPP-%05d", count + 1);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SupplierResponse> getAllSuppliers(String search, Pageable pageable) {
        UUID merchantId = securityUtils.getCurrentUserMerchantId();
        UUID distributorId = merchantId == null ? securityUtils.getDistributorIdForFiltering() : null;

        if (search != null && !search.isBlank()) {
            if (merchantId != null) {
                return supplierRepository.searchByMerchant(merchantId, search, pageable)
                        .map(SupplierResponse::fromEntity);
            }
            if (distributorId != null) {
                return supplierRepository.searchByDistributor(distributorId, search, pageable)
                        .map(SupplierResponse::fromEntity);
            }
            return supplierRepository.searchActive(search, pageable).map(SupplierResponse::fromEntity);
        }

        if (merchantId != null) {
            return supplierRepository.findByDistributorMerchantIdAndActiveTrue(merchantId, pageable)
                    .map(SupplierResponse::fromEntity);
        }
        if (distributorId != null) {
            return supplierRepository.findByDistributorIdAndActiveTrue(distributorId, pageable)
                    .map(SupplierResponse::fromEntity);
        }
        return supplierRepository.findByActiveTrue(pageable).map(SupplierResponse::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SupplierResponse> getBlacklistedSuppliers(Pageable pageable) {
        UUID merchantId = securityUtils.getCurrentUserMerchantId();
        if (merchantId != null) {
            return supplierRepository.findByDistributorMerchantIdAndBlacklistedTrue(merchantId, pageable)
                    .map(SupplierResponse::fromEntity);
        }
        UUID distributorId = securityUtils.getDistributorIdForFiltering();
        if (distributorId != null) {
            return supplierRepository.findByDistributorIdAndBlacklistedTrue(distributorId, pageable)
                    .map(SupplierResponse::fromEntity);
        }
        return supplierRepository.findByBlacklistedTrue(pageable).map(SupplierResponse::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public SupplierResponse getSupplierById(UUID id) {
        return SupplierResponse.fromEntity(findById(id));
    }

    @Override
    @Transactional
    public SupplierResponse createSupplier(SupplierRequest request) {
        log.info("Creating supplier: {}", request.getName());

        if (supplierRepository.existsByPhone(request.getPhone())) {
            throw new DuplicateResourceException("Supplier", "phone", request.getPhone());
        }
        if (request.getEmail() != null && supplierRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Supplier", "email", request.getEmail());
        }
        if (request.getKraPin() != null && supplierRepository.existsByKraPin(request.getKraPin())) {
            throw new DuplicateResourceException("Supplier", "kraPin", request.getKraPin());
        }

        Supplier supplier = Supplier.builder()
                .supplierCode(generateSupplierCode())
                .name(request.getName())
                .kraPin(request.getKraPin())
                .registrationNumber(request.getRegistrationNumber())
                .email(request.getEmail())
                .phone(request.getPhone())
                .address(request.getAddress())
                .city(request.getCity())
                .county(request.getCounty())
                .subCounty(request.getSubCounty())
                .bankName(request.getBankName())
                .bankBranch(request.getBankBranch())
                .bankAccountNumber(request.getBankAccountNumber())
                .bankAccountName(request.getBankAccountName())
                .swiftCode(request.getSwiftCode())
                .paymentTermsDays(request.getPaymentTermsDays() != null ? request.getPaymentTermsDays() : 30)
                .creditLimit(request.getCreditLimit())
                .contactPersons(request.getContactPersons() != null ? request.getContactPersons() : new ArrayList<>())
                .build();

        if (request.getCategoryId() != null) {
            supplier.setCategory(categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("SupplierCategory", "id", request.getCategoryId().toString())));
        }
        if (request.getDistributorId() != null) {
            supplier.setDistributor(distributorRepository.findById(request.getDistributorId())
                    .orElseThrow(() -> new ResourceNotFoundException("Distributor", "id", request.getDistributorId().toString())));
        }

        return SupplierResponse.fromEntity(supplierRepository.save(supplier));
    }

    @Override
    @Transactional
    public SupplierResponse updateSupplier(UUID id, SupplierRequest request) {
        Supplier supplier = findById(id);

        if (!supplier.getPhone().equals(request.getPhone()) && supplierRepository.existsByPhone(request.getPhone())) {
            throw new DuplicateResourceException("Supplier", "phone", request.getPhone());
        }
        if (request.getEmail() != null && !request.getEmail().equals(supplier.getEmail())
                && supplierRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Supplier", "email", request.getEmail());
        }
        if (request.getKraPin() != null && !request.getKraPin().equals(supplier.getKraPin())
                && supplierRepository.existsByKraPin(request.getKraPin())) {
            throw new DuplicateResourceException("Supplier", "kraPin", request.getKraPin());
        }

        supplier.setName(request.getName());
        supplier.setKraPin(request.getKraPin());
        supplier.setRegistrationNumber(request.getRegistrationNumber());
        supplier.setEmail(request.getEmail());
        supplier.setPhone(request.getPhone());
        supplier.setAddress(request.getAddress());
        supplier.setCity(request.getCity());
        supplier.setCounty(request.getCounty());
        supplier.setSubCounty(request.getSubCounty());
        supplier.setBankName(request.getBankName());
        supplier.setBankBranch(request.getBankBranch());
        supplier.setBankAccountNumber(request.getBankAccountNumber());
        supplier.setBankAccountName(request.getBankAccountName());
        supplier.setSwiftCode(request.getSwiftCode());
        if (request.getPaymentTermsDays() != null) supplier.setPaymentTermsDays(request.getPaymentTermsDays());
        if (request.getCreditLimit() != null) supplier.setCreditLimit(request.getCreditLimit());
        if (request.getContactPersons() != null) supplier.setContactPersons(request.getContactPersons());

        if (request.getCategoryId() != null) {
            supplier.setCategory(categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("SupplierCategory", "id", request.getCategoryId().toString())));
        }
        if (request.getDistributorId() != null) {
            supplier.setDistributor(distributorRepository.findById(request.getDistributorId())
                    .orElseThrow(() -> new ResourceNotFoundException("Distributor", "id", request.getDistributorId().toString())));
        }

        return SupplierResponse.fromEntity(supplierRepository.save(supplier));
    }

    @Override
    @Transactional
    public SupplierResponse verifySupplier(UUID id) {
        Supplier supplier = findById(id);
        supplier.setVerified(true);
        return SupplierResponse.fromEntity(supplierRepository.save(supplier));
    }

    @Override
    @Transactional
    public SupplierResponse blacklistSupplier(UUID id, String reason, User currentUser) {
        Supplier supplier = findById(id);
        supplier.setBlacklisted(true);
        supplier.setBlacklistedReason(reason);
        supplier.setBlacklistedAt(LocalDateTime.now());
        supplier.setBlacklistedBy(currentUser);
        supplier.setActive(false);
        return SupplierResponse.fromEntity(supplierRepository.save(supplier));
    }

    @Override
    @Transactional
    public SupplierResponse unblacklistSupplier(UUID id) {
        Supplier supplier = findById(id);
        supplier.setBlacklisted(false);
        supplier.setBlacklistedReason(null);
        supplier.setBlacklistedAt(null);
        supplier.setBlacklistedBy(null);
        supplier.setActive(true);
        return SupplierResponse.fromEntity(supplierRepository.save(supplier));
    }

    @Override
    @Transactional
    public void deactivateSupplier(UUID id, String reason, User currentUser) {
        Supplier supplier = findById(id);
        supplier.setActive(false);
        supplier.setDeactivationReason(reason);
        supplier.setDeactivatedAt(LocalDateTime.now());
        supplier.setDeactivatedBy(currentUser);
        supplierRepository.save(supplier);
    }

    @Override
    @Transactional
    public void activateSupplier(UUID id) {
        Supplier supplier = findById(id);
        supplier.setActive(true);
        supplier.setDeactivationReason(null);
        supplier.setDeactivatedAt(null);
        supplier.setDeactivatedBy(null);
        supplierRepository.save(supplier);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SupplierCategoryResponse> getAllCategories() {
        return categoryRepository.findAll().stream().map(SupplierCategoryResponse::fromEntity).toList();
    }

    @Override
    @Transactional
    public SupplierCategoryResponse createCategory(SupplierCategoryRequest request) {
        if (categoryRepository.existsByName(request.getName())) {
            throw new DuplicateResourceException("SupplierCategory", "name", request.getName());
        }
        SupplierCategory category = SupplierCategory.builder()
                .name(request.getName())
                .description(request.getDescription())
                .build();
        return SupplierCategoryResponse.fromEntity(categoryRepository.save(category));
    }

    @Override
    @Transactional
    public SupplierCategoryResponse updateCategory(Long id, SupplierCategoryRequest request) {
        SupplierCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SupplierCategory", "id", id.toString()));
        if (!category.getName().equals(request.getName()) && categoryRepository.existsByName(request.getName())) {
            throw new DuplicateResourceException("SupplierCategory", "name", request.getName());
        }
        category.setName(request.getName());
        category.setDescription(request.getDescription());
        return SupplierCategoryResponse.fromEntity(categoryRepository.save(category));
    }

    @Override
    @Transactional
    public void deleteCategory(Long id) {
        SupplierCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SupplierCategory", "id", id.toString()));
        categoryRepository.delete(category);
    }

    private Supplier findById(UUID id) {
        return supplierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier", "id", id.toString()));
    }
}
