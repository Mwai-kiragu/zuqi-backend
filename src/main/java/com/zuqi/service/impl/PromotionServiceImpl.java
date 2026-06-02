package com.zuqi.service.impl;

import com.zuqi.api.dto.approval.CreateApprovalRequestDto;
import com.zuqi.api.dto.pricing.CreatePromotionRequest;
import com.zuqi.api.dto.pricing.PromotionResponse;
import com.zuqi.domain.approval.ApprovalWorkflowType;
import com.zuqi.domain.distributor.Distributor;
import com.zuqi.domain.pricing.Promotion;
import com.zuqi.domain.product.Product;
import com.zuqi.exception.ResourceNotFoundException;
import com.zuqi.exception.ValidationException;
import com.zuqi.repository.DistributorRepository;
import com.zuqi.repository.ProductRepository;
import com.zuqi.repository.PromotionRepository;
import com.zuqi.domain.audit.ActivityAction;
import com.zuqi.domain.user.User;
import com.zuqi.service.ActivityLogService;
import com.zuqi.service.ApprovalService;
import com.zuqi.service.PromotionService;
import com.zuqi.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PromotionServiceImpl implements PromotionService {

    private final PromotionRepository   promotionRepository;
    private final ProductRepository     productRepository;
    private final DistributorRepository distributorRepository;
    private final SecurityUtils         securityUtils;
    private final ApprovalService       approvalService;
    private final ActivityLogService    activityLogService;

    @Override
    @Transactional
    public PromotionResponse create(CreatePromotionRequest request) {
        Distributor distributor = resolveDistributor();
        UUID currentUserId = securityUtils.getCurrentUserId();
        boolean needsApproval = securityUtils.currentUserRequiresApprovalFor("PROMOTIONS");

        Product product = request.getProductId() != null
                ? productRepository.findById(request.getProductId())
                        .orElseThrow(() -> new ResourceNotFoundException("Product", "id", request.getProductId()))
                : null;

        Promotion promotion = Promotion.builder()
                .distributor(distributor)
                .name(request.getName())
                .promotionType(request.getPromotionType())
                .discountValue(request.getDiscountValue())
                .minOrderAmount(request.getMinOrderAmount())
                .product(product)
                .categoryId(request.getCategoryId())
                .validFrom(request.getValidFrom())
                .validTo(request.getValidTo())
                .active(true)
                .approvalStatus(needsApproval ? "PENDING_APPROVAL" : "APPROVED")
                .createdById(currentUserId)
                .build();

        Promotion saved = promotionRepository.save(promotion);

        if (needsApproval) {
            approvalService.createRequest(currentUserId,
                    CreateApprovalRequestDto.builder()
                            .workflowType(ApprovalWorkflowType.DISCOUNT_APPROVAL)
                            .entityType("PROMOTION")
                            .entityId(saved.getId())
                            .entityName(saved.getName())
                            .description("New promotion: " + saved.getName())
                            .requestedValues(Map.of(
                                    "name", saved.getName(),
                                    "promotionType", saved.getPromotionType(),
                                    "discountValue", String.valueOf(saved.getDiscountValue())))
                            .requiredApprovals(1)
                            .build());
        }

        User currentUser = securityUtils.getCurrentUser();
        if (currentUser != null) {
            activityLogService.log(
                currentUser.getId(), currentUser.getEmail(),
                currentUser.getFirstName() + " " + currentUser.getLastName(),
                ActivityAction.CREATE, "PROMOTION", saved.getId(),
                saved.getName(), "PROMOTIONS", "Created promotion: " + saved.getName()
            );
        }
        return toResponse(saved);
    }

    @Override
    @Transactional
    public PromotionResponse update(UUID id, CreatePromotionRequest request) {
        Promotion p = findOrThrow(id);
        p.setName(request.getName());
        p.setPromotionType(request.getPromotionType());
        p.setDiscountValue(request.getDiscountValue());
        p.setMinOrderAmount(request.getMinOrderAmount());
        p.setCategoryId(request.getCategoryId());
        p.setValidFrom(request.getValidFrom());
        p.setValidTo(request.getValidTo());
        if (request.getProductId() != null) {
            p.setProduct(productRepository.findById(request.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product", "id", request.getProductId())));
        }
        Promotion updatedPromotion = promotionRepository.save(p);
        User currentUser = securityUtils.getCurrentUser();
        if (currentUser != null) {
            activityLogService.log(
                currentUser.getId(), currentUser.getEmail(),
                currentUser.getFirstName() + " " + currentUser.getLastName(),
                ActivityAction.UPDATE, "PROMOTION", updatedPromotion.getId(),
                updatedPromotion.getName(), "PROMOTIONS", "Updated promotion: " + updatedPromotion.getName()
            );
        }
        return toResponse(updatedPromotion);
    }

    @Override
    public PromotionResponse getById(UUID id) {
        return toResponse(findOrThrow(id));
    }

    @Override
    public Page<PromotionResponse> getAll(Pageable pageable) {
        UUID distId = securityUtils.getDistributorIdForFiltering();
        UUID merchantId = securityUtils.getCurrentUserMerchantId();
        if (distId != null) {
            return promotionRepository.findByDistributorId(distId, pageable).map(this::toResponse);
        } else if (merchantId != null) {
            return promotionRepository.findByDistributorMerchantId(merchantId, pageable).map(this::toResponse);
        }
        return promotionRepository.findAll(pageable).map(this::toResponse);
    }

    @Override
    @Transactional
    public PromotionResponse deactivate(UUID id) {
        Promotion promotion = findOrThrow(id);
        promotion.setActive(false);
        Promotion deactivatedPromotion = promotionRepository.save(promotion);
        User currentUser = securityUtils.getCurrentUser();
        if (currentUser != null) {
            activityLogService.log(
                currentUser.getId(), currentUser.getEmail(),
                currentUser.getFirstName() + " " + currentUser.getLastName(),
                ActivityAction.DEACTIVATE, "PROMOTION", deactivatedPromotion.getId(),
                deactivatedPromotion.getName(), "PROMOTIONS", "Deactivated promotion: " + deactivatedPromotion.getName()
            );
        }
        return toResponse(deactivatedPromotion);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Promotion promotion = findOrThrow(id);
        promotionRepository.delete(promotion);
        User currentUser = securityUtils.getCurrentUser();
        if (currentUser != null) {
            activityLogService.log(
                currentUser.getId(), currentUser.getEmail(),
                currentUser.getFirstName() + " " + currentUser.getLastName(),
                ActivityAction.DELETE, "PROMOTION", promotion.getId(),
                promotion.getName(), "PROMOTIONS", "Deleted promotion: " + promotion.getName()
            );
        }
    }

    private Distributor resolveDistributor() {
        UUID distId = securityUtils.getDistributorIdForFiltering();
        if (distId != null) {
            return distributorRepository.findById(distId)
                    .orElseThrow(() -> new ResourceNotFoundException("Distributor", "id", distId));
        }
        throw new ValidationException("Cannot determine distributor");
    }

    private Promotion findOrThrow(UUID id) {
        return promotionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Promotion", "id", id));
    }

    private PromotionResponse toResponse(Promotion p) {
        return PromotionResponse.builder()
                .id(p.getId())
                .distributorId(p.getDistributor().getId())
                .name(p.getName())
                .promotionType(p.getPromotionType())
                .discountValue(p.getDiscountValue())
                .minOrderAmount(p.getMinOrderAmount())
                .productId(p.getProduct() != null ? p.getProduct().getId() : null)
                .productName(p.getProduct() != null ? p.getProduct().getName() : null)
                .categoryId(p.getCategoryId())
                .validFrom(p.getValidFrom())
                .validTo(p.getValidTo())
                .active(p.isActive())
                .approvalStatus(p.getApprovalStatus())
                .createdById(p.getCreatedById())
                .createdAt(p.getCreatedAt())
                .build();
    }
}
