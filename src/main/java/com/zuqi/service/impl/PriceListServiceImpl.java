package com.zuqi.service.impl;

import com.zuqi.api.dto.approval.CreateApprovalRequestDto;
import com.zuqi.api.dto.pricing.*;
import com.zuqi.domain.approval.ApprovalWorkflowType;
import com.zuqi.domain.distributor.Distributor;
import com.zuqi.domain.pricing.PriceList;
import com.zuqi.domain.pricing.PriceListItem;
import com.zuqi.domain.product.Product;
import com.zuqi.exception.ResourceNotFoundException;
import com.zuqi.exception.ValidationException;
import com.zuqi.repository.DistributorRepository;
import com.zuqi.repository.PriceListRepository;
import com.zuqi.repository.ProductRepository;
import com.zuqi.service.ApprovalService;
import com.zuqi.service.PriceListService;
import com.zuqi.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PriceListServiceImpl implements PriceListService {

    private final PriceListRepository  priceListRepository;
    private final ProductRepository    productRepository;
    private final DistributorRepository distributorRepository;
    private final SecurityUtils        securityUtils;
    private final ApprovalService      approvalService;

    @Override
    @Transactional
    public PriceListResponse create(CreatePriceListRequest request) {
        Distributor distributor = resolveDistributor();
        UUID currentUserId = securityUtils.getCurrentUserId();
        boolean needsApproval = securityUtils.currentUserHasWorkflowTier("INITIATOR");

        PriceList priceList = PriceList.builder()
                .distributor(distributor)
                .name(request.getName())
                .description(request.getDescription())
                .isDefault(request.isDefault())
                .active(true)
                .validFrom(request.getValidFrom())
                .validTo(request.getValidTo())
                .approvalStatus(needsApproval ? "PENDING_APPROVAL" : "APPROVED")
                .createdById(currentUserId)
                .build();

        buildItems(priceList, request.getItems());
        PriceList saved = priceListRepository.save(priceList);

        if (needsApproval) {
            approvalService.createRequest(currentUserId,
                    CreateApprovalRequestDto.builder()
                            .workflowType(ApprovalWorkflowType.PRODUCT_PRICE_EDIT)
                            .entityType("PRICE_LIST")
                            .entityId(saved.getId())
                            .entityName(saved.getName())
                            .description("New price list: " + saved.getName())
                            .requestedValues(Map.of("name", saved.getName(),
                                    "itemCount", String.valueOf(saved.getItems().size())))
                            .requiredApprovals(1)
                            .build());
        }

        return toResponse(saved);
    }

    @Override
    @Transactional
    public PriceListResponse update(UUID id, CreatePriceListRequest request) {
        PriceList priceList = findOrThrow(id);
        priceList.setName(request.getName());
        priceList.setDescription(request.getDescription());
        priceList.setDefault(request.isDefault());
        priceList.setValidFrom(request.getValidFrom());
        priceList.setValidTo(request.getValidTo());
        priceList.getItems().clear();
        buildItems(priceList, request.getItems());
        return toResponse(priceListRepository.save(priceList));
    }

    @Override
    public PriceListResponse getById(UUID id) {
        return toResponse(findOrThrow(id));
    }

    @Override
    public Page<PriceListResponse> getAll(Pageable pageable) {
        UUID distId = securityUtils.getDistributorIdForFiltering();
        UUID merchantId = securityUtils.getCurrentUserMerchantId();
        if (distId != null) {
            return priceListRepository.findByDistributorId(distId, pageable).map(this::toResponse);
        } else if (merchantId != null) {
            return priceListRepository.findByDistributorMerchantId(merchantId, pageable).map(this::toResponse);
        }
        return priceListRepository.findAll(pageable).map(this::toResponse);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        priceListRepository.delete(findOrThrow(id));
    }

    private void buildItems(PriceList priceList, List<PriceListItemRequest> itemRequests) {
        if (itemRequests == null) return;
        List<PriceListItem> items = itemRequests.stream().map(ir -> {
            Product product = productRepository.findById(ir.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product", "id", ir.getProductId()));
            return PriceListItem.builder()
                    .priceList(priceList)
                    .product(product)
                    .unitPrice(ir.getUnitPrice())
                    .discountPercent(ir.getDiscountPercent() != null ? ir.getDiscountPercent() : java.math.BigDecimal.ZERO)
                    .build();
        }).collect(Collectors.toList());
        priceList.getItems().addAll(items);
    }

    private Distributor resolveDistributor() {
        UUID distId = securityUtils.getDistributorIdForFiltering();
        if (distId != null) {
            return distributorRepository.findById(distId)
                    .orElseThrow(() -> new ResourceNotFoundException("Distributor", "id", distId));
        }
        throw new ValidationException("Cannot determine distributor for price list");
    }

    private PriceList findOrThrow(UUID id) {
        return priceListRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PriceList", "id", id));
    }

    private PriceListResponse toResponse(PriceList pl) {
        List<PriceListResponse.ItemResponse> items = pl.getItems().stream()
                .map(i -> PriceListResponse.ItemResponse.builder()
                        .id(i.getId())
                        .productId(i.getProduct().getId())
                        .productName(i.getProduct().getName())
                        .unitPrice(i.getUnitPrice())
                        .discountPercent(i.getDiscountPercent())
                        .build())
                .collect(Collectors.toList());
        return PriceListResponse.builder()
                .id(pl.getId())
                .distributorId(pl.getDistributor().getId())
                .name(pl.getName())
                .description(pl.getDescription())
                .isDefault(pl.isDefault())
                .active(pl.isActive())
                .validFrom(pl.getValidFrom())
                .validTo(pl.getValidTo())
                .items(items)
                .approvalStatus(pl.getApprovalStatus())
                .createdById(pl.getCreatedById())
                .createdAt(pl.getCreatedAt())
                .build();
    }
}
