package com.zuqi.service.impl;

import com.zuqi.api.dto.inventory.ProductBatchRequest;
import com.zuqi.api.dto.inventory.ProductBatchResponse;
import com.zuqi.domain.distributor.Distributor;
import com.zuqi.domain.inventory.ProductBatch;
import com.zuqi.domain.inventory.Warehouse;
import com.zuqi.domain.product.Product;
import com.zuqi.exception.ResourceNotFoundException;
import com.zuqi.exception.ValidationException;
import com.zuqi.repository.DistributorRepository;
import com.zuqi.repository.ProductBatchRepository;
import com.zuqi.repository.ProductRepository;
import com.zuqi.repository.WarehouseRepository;
import com.zuqi.service.ProductBatchService;
import com.zuqi.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductBatchServiceImpl implements ProductBatchService {

    private final ProductBatchRepository productBatchRepository;
    private final ProductRepository      productRepository;
    private final WarehouseRepository    warehouseRepository;
    private final DistributorRepository  distributorRepository;
    private final SecurityUtils          securityUtils;

    @Override
    @Transactional
    public ProductBatchResponse create(ProductBatchRequest request) {
        UUID distId = securityUtils.getDistributorIdForFiltering();
        if (distId == null) throw new ValidationException("Cannot determine distributor for batch");

        Distributor distributor = distributorRepository.findById(distId)
                .orElseThrow(() -> new ResourceNotFoundException("Distributor", "id", distId));

        Warehouse warehouse = warehouseRepository.findById(request.getWarehouseId())
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse", "id", request.getWarehouseId()));

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", request.getProductId()));

        ProductBatch batch = ProductBatch.builder()
                .distributor(distributor)
                .warehouse(warehouse)
                .product(product)
                .batchNumber(request.getBatchNumber())
                .manufactureDate(request.getManufactureDate())
                .expiryDate(request.getExpiryDate())
                .initialQuantity(request.getInitialQuantity())
                .currentQuantity(request.getInitialQuantity())
                .status("ACTIVE")
                .updatedAt(LocalDateTime.now())
                .build();

        return toResponse(productBatchRepository.save(batch));
    }

    @Override
    public ProductBatchResponse getById(UUID id) {
        return toResponse(findOrThrow(id));
    }

    @Override
    public Page<ProductBatchResponse> getAll(Pageable pageable) {
        UUID distId = securityUtils.getDistributorIdForFiltering();
        if (distId != null) {
            return productBatchRepository.findByDistributorIdAndStatus(distId, "ACTIVE")
                    .stream().map(this::toResponse).collect(Collectors.toList())
                    .stream().collect(Collectors.collectingAndThen(
                            Collectors.toList(),
                            list -> new org.springframework.data.domain.PageImpl<>(list, pageable, list.size())));
        }
        return productBatchRepository.findAll(pageable).map(this::toResponse);
    }

    @Override
    public List<ProductBatchResponse> getExpiringSoon(int daysAhead) {
        UUID distId = securityUtils.getDistributorIdForFiltering();
        if (distId == null) distId = securityUtils.getCurrentUserMerchantId();
        LocalDate endDate = LocalDate.now().plusDays(daysAhead);
        return productBatchRepository.findExpiringBatches(distId, endDate)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ProductBatchResponse updateQuantity(UUID id, Double newQuantity) {
        ProductBatch batch = findOrThrow(id);
        batch.setCurrentQuantity(newQuantity);
        batch.setUpdatedAt(LocalDateTime.now());
        if (newQuantity <= 0) batch.setStatus("DEPLETED");
        return toResponse(productBatchRepository.save(batch));
    }

    private ProductBatch findOrThrow(UUID id) {
        return productBatchRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ProductBatch", "id", id));
    }

    private ProductBatchResponse toResponse(ProductBatch b) {
        boolean expiringSoon = b.getExpiryDate() != null
                && !b.getExpiryDate().isBefore(LocalDate.now())
                && !b.getExpiryDate().isAfter(LocalDate.now().plusDays(30));
        return ProductBatchResponse.builder()
                .id(b.getId())
                .distributorId(b.getDistributor().getId())
                .warehouseId(b.getWarehouse().getId())
                .warehouseName(b.getWarehouse().getName())
                .productId(b.getProduct().getId())
                .productName(b.getProduct().getName())
                .batchNumber(b.getBatchNumber())
                .manufactureDate(b.getManufactureDate())
                .expiryDate(b.getExpiryDate())
                .initialQuantity(b.getInitialQuantity())
                .currentQuantity(b.getCurrentQuantity())
                .status(b.getStatus())
                .createdAt(b.getCreatedAt())
                .expiringSoon(expiringSoon)
                .build();
    }
}
