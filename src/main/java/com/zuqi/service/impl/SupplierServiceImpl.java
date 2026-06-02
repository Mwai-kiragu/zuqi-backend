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
import com.zuqi.exception.ValidationException;
import com.zuqi.api.dto.approval.CreateApprovalRequestDto;
import com.zuqi.domain.approval.ApprovalWorkflowType;
import com.zuqi.repository.DistributorRepository;
import com.zuqi.repository.SupplierCategoryRepository;
import com.zuqi.repository.SupplierRepository;
import com.zuqi.domain.audit.ActivityAction;
import com.zuqi.service.ActivityLogService;
import com.zuqi.service.ApprovalService;
import com.zuqi.service.SupplierService;
import com.zuqi.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zuqi.api.dto.approval.ApprovalRequestResponse;
import com.zuqi.domain.approval.ApprovalStatus;
import com.zuqi.repository.ApprovalRequestRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SupplierServiceImpl implements SupplierService {

    private final SupplierRepository supplierRepository;
    private final SupplierCategoryRepository categoryRepository;
    private final DistributorRepository distributorRepository;
    private final ApprovalRequestRepository approvalRequestRepository;
    private final SecurityUtils securityUtils;
    private final ApprovalService approvalService;
    private final ActivityLogService activityLogService;

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
        } else {
            // Auto-resolve distributor from security context (DISTRIBUTOR_ADMIN or MERCHANT_ADMIN)
            UUID distributorId = securityUtils.getDistributorIdForFiltering();
            if (distributorId != null) {
                distributorRepository.findById(distributorId).ifPresent(supplier::setDistributor);
            } else {
                UUID merchantId = securityUtils.getCurrentUserMerchantId();
                if (merchantId != null) {
                    distributorRepository.findFirstByMerchantId(merchantId).ifPresent(supplier::setDistributor);
                }
            }
        }

        boolean needsApproval = securityUtils.currentUserRequiresApprovalFor("SUPPLIERS");
        supplier.setApprovalStatus(needsApproval ? "PENDING_APPROVAL" : "APPROVED");
        UUID currentUserId = securityUtils.getCurrentUserId();
        supplier.setCreatedById(currentUserId);

        Supplier saved = supplierRepository.save(supplier);

        if (needsApproval && currentUserId != null) {
            approvalService.createRequest(currentUserId, CreateApprovalRequestDto.builder()
                    .workflowType(ApprovalWorkflowType.SUPPLIER_CREATION)
                    .entityType("SUPPLIER")
                    .entityId(saved.getId())
                    .entityName(saved.getName())
                    .description("New supplier: " + saved.getName())
                    .requestedValues(Map.of(
                            "name", saved.getName(),
                            "phone", saved.getPhone(),
                            "kraPin", Objects.toString(saved.getKraPin(), "")))
                    .requiredApprovals(1)
                    .build());
        }

        User currentUser = securityUtils.getCurrentUser();
        if (currentUser != null) {
            activityLogService.log(
                currentUser.getId(), currentUser.getEmail(),
                currentUser.getFirstName() + " " + currentUser.getLastName(),
                ActivityAction.CREATE, "SUPPLIER", saved.getId(),
                saved.getName(), "SUPPLIERS", "Created supplier: " + saved.getName()
            );
        }
        return SupplierResponse.fromEntity(saved);
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

        boolean needsApproval = securityUtils.currentUserRequiresApprovalFor("SUPPLIERS");
        UUID currentUserId = securityUtils.getCurrentUserId();

        if (needsApproval && currentUserId != null) {
            boolean hasPending = !approvalRequestRepository
                    .findByEntityTypeAndEntityIdAndStatus("SUPPLIER_UPDATE", id, ApprovalStatus.PENDING)
                    .isEmpty();
            if (hasPending) {
                throw new ValidationException("This supplier already has a pending approval request. Please wait for it to be resolved before submitting new changes.");
            }

            Map<String, Object> pendingValues = new LinkedHashMap<>();
            pendingValues.put("name", request.getName());
            pendingValues.put("kraPin", request.getKraPin());
            if (request.getRegistrationNumber() != null) pendingValues.put("registrationNumber", request.getRegistrationNumber());
            if (request.getEmail() != null) pendingValues.put("email", request.getEmail());
            pendingValues.put("phone", request.getPhone());
            if (request.getAddress() != null) pendingValues.put("address", request.getAddress());
            if (request.getCity() != null) pendingValues.put("city", request.getCity());
            if (request.getCounty() != null) pendingValues.put("county", request.getCounty());
            if (request.getSubCounty() != null) pendingValues.put("subCounty", request.getSubCounty());
            if (request.getBankName() != null) pendingValues.put("bankName", request.getBankName());
            if (request.getBankBranch() != null) pendingValues.put("bankBranch", request.getBankBranch());
            if (request.getBankAccountNumber() != null) pendingValues.put("bankAccountNumber", request.getBankAccountNumber());
            if (request.getBankAccountName() != null) pendingValues.put("bankAccountName", request.getBankAccountName());
            if (request.getSwiftCode() != null) pendingValues.put("swiftCode", request.getSwiftCode());
            if (request.getPaymentTermsDays() != null) pendingValues.put("paymentTermsDays", request.getPaymentTermsDays());
            if (request.getCreditLimit() != null) pendingValues.put("creditLimit", request.getCreditLimit().toString());
            if (request.getCategoryId() != null) pendingValues.put("categoryId", request.getCategoryId().toString());
            if (request.getContactPersons() != null) pendingValues.put("contactPersons", request.getContactPersons());

            ApprovalRequestResponse approvalReq = approvalService.createRequest(currentUserId,
                    CreateApprovalRequestDto.builder()
                            .workflowType(ApprovalWorkflowType.SUPPLIER_DETAILS_UPDATE)
                            .entityType("SUPPLIER_UPDATE")
                            .entityId(supplier.getId())
                            .entityName(supplier.getName())
                            .description("Update details for supplier: " + supplier.getName())
                            .requestedValues(pendingValues)
                            .requiredApprovals(1)
                            .build());

            User currentUser = securityUtils.getCurrentUser();
            if (currentUser != null) {
                activityLogService.log(
                        currentUser.getId(), currentUser.getEmail(),
                        currentUser.getFirstName() + " " + currentUser.getLastName(),
                        ActivityAction.UPDATE, "SUPPLIER", supplier.getId(),
                        supplier.getName(), "SUPPLIERS", "Submitted update for approval: " + supplier.getName());
            }

            SupplierResponse response = SupplierResponse.fromEntity(supplier);
            response.setPendingApprovalId(approvalReq.getId());
            return response;
        }

        // No approval needed — apply immediately
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

        Supplier updatedSupplier = supplierRepository.save(supplier);
        User currentUser = securityUtils.getCurrentUser();
        if (currentUser != null) {
            activityLogService.log(
                    currentUser.getId(), currentUser.getEmail(),
                    currentUser.getFirstName() + " " + currentUser.getLastName(),
                    ActivityAction.UPDATE, "SUPPLIER", updatedSupplier.getId(),
                    updatedSupplier.getName(), "SUPPLIERS", "Updated supplier: " + updatedSupplier.getName());
        }
        return SupplierResponse.fromEntity(updatedSupplier);
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
        if (currentUser != null) {
            activityLogService.log(
                currentUser.getId(), currentUser.getEmail(),
                currentUser.getFirstName() + " " + currentUser.getLastName(),
                ActivityAction.DEACTIVATE, "SUPPLIER", supplier.getId(),
                supplier.getName(), "SUPPLIERS", "Deactivated supplier: " + supplier.getName()
            );
        }
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
        User currentUser = securityUtils.getCurrentUser();
        if (currentUser != null) {
            activityLogService.log(
                currentUser.getId(), currentUser.getEmail(),
                currentUser.getFirstName() + " " + currentUser.getLastName(),
                ActivityAction.ACTIVATE, "SUPPLIER", supplier.getId(),
                supplier.getName(), "SUPPLIERS", "Activated supplier: " + supplier.getName()
            );
        }
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
