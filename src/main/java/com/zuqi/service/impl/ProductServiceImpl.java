package com.zuqi.service.impl;

import com.zuqi.api.dto.approval.CreateApprovalRequestDto;
import com.zuqi.api.dto.product.ProductBranchPriceRequest;
import com.zuqi.api.dto.product.ProductBranchPriceResponse;
import com.zuqi.api.dto.product.ProductCategoryRequest;
import com.zuqi.api.dto.product.ProductCategoryResponse;
import com.zuqi.api.dto.product.ProductRequest;
import com.zuqi.api.dto.product.ProductResponse;
import com.zuqi.domain.approval.ApprovalWorkflowType;
import com.zuqi.domain.branch.DistributorBranch;
import com.zuqi.domain.distributor.Distributor;
import com.zuqi.domain.gl.GlAccount;
import com.zuqi.domain.product.Product;
import com.zuqi.domain.product.ProductBranchPrice;
import com.zuqi.domain.product.ProductCategory;
import com.zuqi.domain.user.User;
import com.zuqi.exception.DuplicateResourceException;
import com.zuqi.exception.ResourceNotFoundException;
import com.zuqi.api.dto.inventory.StockAdjustmentRequest;
import com.zuqi.domain.inventory.Stock;
import com.zuqi.domain.inventory.StockMovement;
import com.zuqi.repository.DistributorBranchRepository;
import com.zuqi.repository.DistributorRepository;
import com.zuqi.repository.GlAccountRepository;
import com.zuqi.repository.ProductBranchPriceRepository;
import com.zuqi.repository.ProductCategoryRepository;
import com.zuqi.repository.ProductRepository;
import com.zuqi.repository.StockRepository;
import com.zuqi.repository.WarehouseRepository;
import com.zuqi.service.ApprovalService;
import com.zuqi.service.InventoryService;
import com.zuqi.service.ProductService;
import com.zuqi.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductCategoryRepository categoryRepository;
    private final DistributorRepository distributorRepository;
    private final DistributorBranchRepository branchRepository;
    private final ProductBranchPriceRepository branchPriceRepository;
    private final StockRepository stockRepository;
    private final WarehouseRepository warehouseRepository;
    private final GlAccountRepository glAccountRepository;
    private final SecurityUtils securityUtils;
    private final ApprovalService approvalService;
    private final InventoryService inventoryService;

    @Override
    @Transactional(readOnly = true)
    public Page<ProductResponse> getAllProducts(java.time.LocalDate startDate, java.time.LocalDate endDate, Pageable pageable) {
        log.debug("Fetching all products");
        boolean hasDates = startDate != null && endDate != null;
        java.time.LocalDateTime from = hasDates ? startDate.atStartOfDay() : null;
        java.time.LocalDateTime to = hasDates ? endDate.plusDays(1).atStartOfDay() : null;

        UUID merchantId = securityUtils.getCurrentUserMerchantId();
        if (merchantId != null) {
            if (hasDates)
                return enrichWithStockAndBranchPrices(productRepository.findByDistributorMerchantIdAndActiveTrueAndCreatedAtBetween(merchantId, from, to, pageable));
            return enrichWithStockAndBranchPrices(productRepository.findByDistributorMerchantIdAndActiveTrue(merchantId, pageable));
        }
        UUID distributorId = securityUtils.getDistributorIdForFiltering();
        if (distributorId != null) {
            if (hasDates)
                return enrichWithStockAndBranchPrices(productRepository.findByDistributorIdAndActiveTrueAndCreatedAtBetween(distributorId, from, to, pageable));
            return enrichWithStockAndBranchPrices(productRepository.findByDistributorIdAndActiveTrue(distributorId, pageable));
        }

        if (hasDates)
            return enrichWithStockAndBranchPrices(productRepository.findByActiveTrueAndCreatedAtBetween(from, to, pageable));
        return enrichWithStockAndBranchPrices(productRepository.findByActiveTrue(pageable));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductResponse> getProductsByDistributor(UUID distributorId, java.time.LocalDate startDate, java.time.LocalDate endDate, Pageable pageable) {
        log.debug("Fetching products for distributor: {}", distributorId);
        boolean hasDates = startDate != null && endDate != null;
        if (hasDates) {
            java.time.LocalDateTime from = startDate.atStartOfDay();
            java.time.LocalDateTime to = endDate.plusDays(1).atStartOfDay();
            return enrichWithStockAndBranchPrices(productRepository.findByDistributorIdAndActiveTrueAndCreatedAtBetween(distributorId, from, to, pageable));
        }
        return enrichWithStockAndBranchPrices(productRepository.findByDistributorIdAndActiveTrue(distributorId, pageable));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductResponse> getProductsByCategory(Long categoryId, Pageable pageable) {
        log.debug("Fetching products for category: {}", categoryId);
        return enrichWithStockAndBranchPrices(productRepository.findByCategoryIdAndActiveTrue(categoryId, pageable));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductResponse> searchProducts(String searchTerm, UUID distributorId, Pageable pageable) {
        log.debug("Searching products with term: {}, distributor: {}", searchTerm, distributorId);

        UUID effectiveDistributorId = distributorId;
        if (effectiveDistributorId == null) {
            UUID merchantId = securityUtils.getCurrentUserMerchantId();
            if (merchantId != null) {
                return enrichWithStockAndBranchPrices(productRepository.searchByMerchant(merchantId, searchTerm, pageable));
            }
            effectiveDistributorId = securityUtils.getDistributorIdForFiltering();
        }

        if (effectiveDistributorId != null) {
            return enrichWithStockAndBranchPrices(productRepository.searchByDistributor(effectiveDistributorId, searchTerm, pageable));
        }

        return enrichWithStockAndBranchPrices(productRepository.searchByNameOrSku(searchTerm, pageable));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductResponse> getProductsForBranch(UUID distributorId, UUID branchId, String search, Long categoryId, Pageable pageable) {
        log.debug("Fetching products for distributor: {} branch: {} search: {} category: {}", distributorId, branchId, search, categoryId);

        Page<Product> page;
        boolean hasSearch = search != null && !search.isBlank();
        if (categoryId != null && hasSearch) {
            page = productRepository.searchAvailableByDistributorAndBranchAndCategory(distributorId, branchId, categoryId, search, pageable);
        } else if (categoryId != null) {
            page = productRepository.findAvailableByDistributorAndBranchAndCategory(distributorId, branchId, categoryId, pageable);
        } else if (hasSearch) {
            page = productRepository.searchAvailableByDistributorAndBranch(distributorId, branchId, search, pageable);
        } else {
            page = productRepository.findAvailableByDistributorAndBranch(distributorId, branchId, pageable);
        }

        return enrichWithStockAndBranchPrices(page);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getProductById(UUID id) {
        log.debug("Fetching product by ID: {}", id);
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id.toString()));
        ProductResponse response = ProductResponse.fromEntity(product);
        List<ProductBranchPrice> prices = branchPriceRepository.findByProductId(id);
        if (!prices.isEmpty()) {
            response.setBranchPrices(prices.stream()
                    .map(ProductBranchPriceResponse::fromEntity)
                    .collect(Collectors.toList()));
        }
        enrichGlAccountNames(List.of(response));
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getProductBySku(String sku, UUID distributorId) {
        log.debug("Fetching product by SKU: {} for distributor: {}", sku, distributorId);
        Product product = productRepository.findBySkuAndDistributorId(sku, distributorId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "sku", sku));
        return ProductResponse.fromEntity(product);
    }

    @Override
    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        log.info("Creating new product: {}", request.getName());

        Distributor distributor = distributorRepository.findById(request.getDistributorId())
                .orElseThrow(() -> new ResourceNotFoundException("Distributor", "id", request.getDistributorId().toString()));

        if (productRepository.existsBySkuAndDistributorId(request.getSku(), request.getDistributorId())) {
            throw new DuplicateResourceException("Product", "sku", request.getSku());
        }

        Product product = Product.builder()
                .distributor(distributor)
                .sku(request.getSku())
                .name(request.getName())
                .description(request.getDescription())
                .unitOfMeasure(request.getUnitOfMeasure() != null ? request.getUnitOfMeasure() : "PIECE")
                .unitPrice(request.getUnitPrice())
                .costPrice(request.getCostPrice())
                .imageUrl(request.getImageUrl())
                .barcode(request.getBarcode())
                .allBranches(request.isAllBranches())
                .revenueAccountId(request.getRevenueAccountId())
                .cogsAccountId(request.getCogsAccountId())
                .minSalePrice(request.getMinSalePrice())
                .build();

        if (request.getCategoryId() != null) {
            ProductCategory category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("ProductCategory", "id", request.getCategoryId().toString()));
            product.setCategory(category);
        }

        boolean needsApproval = securityUtils.currentUserHasWorkflowTier("INITIATOR");
        product.setApprovalStatus(needsApproval ? "PENDING_APPROVAL" : "APPROVED");
        UUID currentUserId = securityUtils.getCurrentUserId();
        product.setCreatedById(currentUserId);

        Product savedProduct = productRepository.save(product);
        log.info("Product created successfully with ID: {}", savedProduct.getId());

        if (needsApproval && currentUserId != null) {
            approvalService.createRequest(currentUserId, CreateApprovalRequestDto.builder()
                    .workflowType(ApprovalWorkflowType.PRODUCT_PRICE_EDIT)
                    .entityType("PRODUCT")
                    .entityId(savedProduct.getId())
                    .entityName(savedProduct.getName())
                    .description("New product: " + savedProduct.getName())
                    .requestedValues(Map.of(
                            "name", savedProduct.getName(),
                            "sku", savedProduct.getSku(),
                            "unitPrice", Objects.toString(savedProduct.getUnitPrice(), "")))
                    .requiredApprovals(1)
                    .build());
        }

        if (request.getBranchPrices() != null && !request.getBranchPrices().isEmpty()) {
            saveBranchPrices(savedProduct, request.getBranchPrices());
        }

        // Opening stock — create a Stock record in the specified warehouse
        if (request.getOpeningStockWarehouseId() != null) {
            UUID warehouseId = request.getOpeningStockWarehouseId();
            warehouseRepository.findById(warehouseId).ifPresent(warehouse -> {
                BigDecimal qty = request.getOpeningStockQuantity() != null
                        ? request.getOpeningStockQuantity() : BigDecimal.ZERO;

                if (qty.compareTo(BigDecimal.ZERO) > 0) {
                    // Use InventoryService so the INITIATOR check + approval flow applies automatically
                    try {
                        StockAdjustmentRequest stockReq = StockAdjustmentRequest.builder()
                                .warehouseId(warehouseId)
                                .productId(savedProduct.getId())
                                .quantity(qty)
                                .movementType(StockMovement.MovementType.IN)
                                .referenceType("PRODUCT_CREATION")
                                .referenceId(savedProduct.getId())
                                .notes("Opening stock for new product: " + savedProduct.getName())
                                .build();
                        inventoryService.adjustStock(stockReq, securityUtils.getCurrentUserId());
                    } catch (Exception e) {
                        log.warn("Could not create opening stock movement for product {}: {}", savedProduct.getId(), e.getMessage());
                    }
                } else {
                    // Quantity = 0: create a zero-quantity Stock record so the product appears in inventory
                    boolean exists = stockRepository.findByWarehouseIdAndProductId(warehouseId, savedProduct.getId()).isPresent();
                    if (!exists) {
                        stockRepository.save(Stock.builder()
                                .warehouse(warehouse)
                                .product(savedProduct)
                                .quantity(BigDecimal.ZERO)
                                .reservedQuantity(BigDecimal.ZERO)
                                .build());
                        log.info("Created zero-quantity stock record for product {} in warehouse {}", savedProduct.getId(), warehouseId);
                    }
                }
            });
        }

        ProductResponse response = ProductResponse.fromEntity(savedProduct);
        List<ProductBranchPrice> prices = branchPriceRepository.findByProductId(savedProduct.getId());
        if (!prices.isEmpty()) {
            response.setBranchPrices(prices.stream()
                    .map(ProductBranchPriceResponse::fromEntity)
                    .collect(Collectors.toList()));
        }
        return response;
    }

    @Override
    @Transactional
    public ProductResponse updateProduct(UUID id, ProductRequest request) {
        log.info("Updating product: {}", id);

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id.toString()));

        if (!product.getSku().equals(request.getSku()) &&
                productRepository.existsBySkuAndDistributorId(request.getSku(), product.getDistributor().getId())) {
            throw new DuplicateResourceException("Product", "sku", request.getSku());
        }

        product.setSku(request.getSku());
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setUnitPrice(request.getUnitPrice());
        product.setAllBranches(request.isAllBranches());

        if (request.getUnitOfMeasure() != null) {
            product.setUnitOfMeasure(request.getUnitOfMeasure());
        }
        if (request.getCostPrice() != null) {
            product.setCostPrice(request.getCostPrice());
        }
        if (request.getImageUrl() != null) {
            product.setImageUrl(request.getImageUrl());
        }
        if (request.getBarcode() != null) {
            product.setBarcode(request.getBarcode());
        }

        if (request.getCategoryId() != null) {
            ProductCategory category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("ProductCategory", "id", request.getCategoryId().toString()));
            product.setCategory(category);
        }

        // GL account overrides (null clears the override)
        product.setRevenueAccountId(request.getRevenueAccountId());
        product.setCogsAccountId(request.getCogsAccountId());
        product.setMinSalePrice(request.getMinSalePrice());

        Product updatedProduct = productRepository.save(product);
        log.info("Product updated successfully: {}", id);

        // Replace branch prices
        branchPriceRepository.deleteByProductId(id);
        if (request.getBranchPrices() != null && !request.getBranchPrices().isEmpty()) {
            saveBranchPrices(updatedProduct, request.getBranchPrices());
        }

        ProductResponse response = ProductResponse.fromEntity(updatedProduct);
        List<ProductBranchPrice> prices = branchPriceRepository.findByProductId(id);
        if (!prices.isEmpty()) {
            response.setBranchPrices(prices.stream()
                    .map(ProductBranchPriceResponse::fromEntity)
                    .collect(Collectors.toList()));
        }
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductResponse> getInactiveProducts(Pageable pageable) {
        log.debug("Fetching all inactive products");

        UUID merchantId = securityUtils.getCurrentUserMerchantId();
        if (merchantId != null) {
            return enrichWithStockAndBranchPrices(productRepository.findByDistributorMerchantIdAndActiveFalse(merchantId, pageable));
        }
        UUID distributorId = securityUtils.getDistributorIdForFiltering();
        if (distributorId != null) {
            return enrichWithStockAndBranchPrices(productRepository.findByDistributorIdAndActiveFalse(distributorId, pageable));
        }

        return enrichWithStockAndBranchPrices(productRepository.findByActiveFalse(pageable));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductResponse> getInactiveProductsByDistributor(UUID distributorId, Pageable pageable) {
        log.debug("Fetching inactive products for distributor: {}", distributorId);
        return enrichWithStockAndBranchPrices(productRepository.findByDistributorIdAndActiveFalse(distributorId, pageable));
    }

    @Override
    @Transactional
    public void deactivateProduct(UUID id, String reason, User currentUser) {
        log.info("Deactivating product: {} with reason: {}", id, reason);

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id.toString()));

        product.setActive(false);
        product.setDeactivationReason(reason);
        product.setDeactivatedAt(LocalDateTime.now());
        product.setDeactivatedBy(currentUser);
        productRepository.save(product);

        log.info("Product deactivated successfully");
    }

    @Override
    @Transactional
    public void activateProduct(UUID id) {
        log.info("Activating product: {}", id);

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id.toString()));

        product.setActive(true);
        product.setDeactivationReason(null);
        product.setDeactivatedAt(null);
        product.setDeactivatedBy(null);
        productRepository.save(product);

        log.info("Product activated successfully");
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductCategoryResponse> getAllCategories(UUID distributorId) {
        log.debug("Fetching active product categories for distributor: {}", distributorId);

        UUID effectiveDistributorId = distributorId;
        if (effectiveDistributorId == null) {
            UUID merchantId = securityUtils.getCurrentUserMerchantId();
            if (merchantId != null) {
                return categoryRepository.findByDistributorMerchantIdAndActiveTrue(merchantId).stream()
                        .map(ProductCategoryResponse::fromEntity)
                        .toList();
            }
            effectiveDistributorId = securityUtils.getDistributorIdForFiltering();
        }

        if (effectiveDistributorId != null) {
            return categoryRepository.findByDistributorIdAndActiveTrue(effectiveDistributorId).stream()
                    .map(ProductCategoryResponse::fromEntity)
                    .toList();
        }

        return categoryRepository.findAll().stream()
                .filter(ProductCategory::isActive)
                .map(ProductCategoryResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductCategoryResponse> getInactiveCategories(UUID distributorId) {
        log.debug("Fetching inactive product categories for distributor: {}", distributorId);

        UUID effectiveDistributorId = distributorId;
        if (effectiveDistributorId == null) {
            UUID merchantId = securityUtils.getCurrentUserMerchantId();
            if (merchantId != null) {
                return categoryRepository.findByDistributorMerchantIdAndActiveFalse(merchantId).stream()
                        .map(ProductCategoryResponse::fromEntity)
                        .toList();
            }
            effectiveDistributorId = securityUtils.getDistributorIdForFiltering();
        }

        if (effectiveDistributorId != null) {
            return categoryRepository.findByDistributorIdAndActiveFalse(effectiveDistributorId).stream()
                    .map(ProductCategoryResponse::fromEntity)
                    .toList();
        }

        return categoryRepository.findAll().stream()
                .filter(c -> !c.isActive())
                .map(ProductCategoryResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ProductCategoryResponse getCategoryById(Long id) {
        log.debug("Fetching product category by ID: {}", id);
        ProductCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ProductCategory", "id", id.toString()));
        return ProductCategoryResponse.fromEntity(category);
    }

    @Override
    @Transactional
    public ProductCategoryResponse createCategory(ProductCategoryRequest request) {
        log.info("Creating new product category: {}", request.getName());

        Distributor distributor = distributorRepository.findById(request.getDistributorId())
                .orElseThrow(() -> new ResourceNotFoundException("Distributor", "id", request.getDistributorId().toString()));

        if (categoryRepository.existsByNameAndDistributorId(request.getName(), request.getDistributorId())) {
            throw new DuplicateResourceException("ProductCategory", "name", request.getName());
        }

        ProductCategory category = ProductCategory.builder()
                .name(request.getName())
                .description(request.getDescription())
                .distributor(distributor)
                .build();

        if (request.getParentId() != null) {
            ProductCategory parent = categoryRepository.findById(request.getParentId())
                    .orElseThrow(() -> new ResourceNotFoundException("ProductCategory", "id", request.getParentId().toString()));
            category.setParent(parent);
        }

        ProductCategory savedCategory = categoryRepository.save(category);
        log.info("Product category created successfully with ID: {}", savedCategory.getId());

        return ProductCategoryResponse.fromEntity(savedCategory);
    }

    @Override
    @Transactional
    public ProductCategoryResponse updateCategory(Long id, ProductCategoryRequest request) {
        log.info("Updating product category: {}", id);

        ProductCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ProductCategory", "id", id.toString()));

        if (!category.getName().equals(request.getName()) &&
                categoryRepository.existsByNameAndDistributorId(request.getName(), category.getDistributor().getId())) {
            throw new DuplicateResourceException("ProductCategory", "name", request.getName());
        }

        category.setName(request.getName());
        category.setDescription(request.getDescription());

        if (request.getParentId() != null) {
            ProductCategory parent = categoryRepository.findById(request.getParentId())
                    .orElseThrow(() -> new ResourceNotFoundException("ProductCategory", "id", request.getParentId().toString()));
            category.setParent(parent);
        } else {
            category.setParent(null);
        }

        ProductCategory updatedCategory = categoryRepository.save(category);
        log.info("Product category updated successfully: {}", id);

        return ProductCategoryResponse.fromEntity(updatedCategory);
    }

    @Override
    @Transactional
    public void deactivateCategory(Long id, String reason, User currentUser) {
        log.info("Deactivating product category: {} with reason: {}", id, reason);

        ProductCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ProductCategory", "id", id.toString()));

        category.setActive(false);
        category.setDeactivationReason(reason);
        category.setDeactivatedAt(LocalDateTime.now());
        category.setDeactivatedBy(currentUser);
        categoryRepository.save(category);

        log.info("Product category deactivated successfully: {}", id);
    }

    @Override
    @Transactional
    public void activateCategory(Long id) {
        log.info("Activating product category: {}", id);

        ProductCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ProductCategory", "id", id.toString()));

        category.setActive(true);
        category.setDeactivationReason(null);
        category.setDeactivatedAt(null);
        category.setDeactivatedBy(null);
        categoryRepository.save(category);

        log.info("Product category activated successfully: {}", id);
    }

    // -------------------------------------------------------
    // Helpers
    // -------------------------------------------------------

    private void saveBranchPrices(Product product, List<ProductBranchPriceRequest> requests) {
        for (ProductBranchPriceRequest req : requests) {
            DistributorBranch branch = branchRepository.findById(req.getBranchId())
                    .orElseThrow(() -> new ResourceNotFoundException("DistributorBranch", "id", req.getBranchId().toString()));
            ProductBranchPrice pbp = ProductBranchPrice.builder()
                    .product(product)
                    .branch(branch)
                    .unitPrice(req.getUnitPrice())
                    .active(req.isActive())
                    .build();
            branchPriceRepository.save(pbp);
        }
    }

    private Page<ProductResponse> enrichWithStockAndBranchPrices(Page<Product> page) {
        if (page.isEmpty()) {
            return page.map(ProductResponse::fromEntity);
        }
        List<UUID> ids = page.stream().map(Product::getId).collect(Collectors.toList());

        // Stock
        Map<UUID, BigDecimal> stockMap = stockRepository.findTotalStockByProductIds(ids).stream()
                .collect(Collectors.toMap(
                        row -> (UUID) row[0],
                        row -> (BigDecimal) row[1]
                ));

        // Branch prices (batch load to avoid N+1)
        Map<UUID, List<ProductBranchPriceResponse>> branchPricesMap = branchPriceRepository.findByProductIdIn(ids).stream()
                .collect(Collectors.groupingBy(
                        pbp -> pbp.getProduct().getId(),
                        Collectors.mapping(ProductBranchPriceResponse::fromEntity, Collectors.toList())
                ));

        // Build response map keyed by product ID so we can enrich GL names before returning
        Map<UUID, ProductResponse> responseMap = new java.util.LinkedHashMap<>();
        for (Product p : page) {
            ProductResponse r = ProductResponse.fromEntity(p);
            r.setTotalStock(stockMap.getOrDefault(p.getId(), BigDecimal.ZERO));
            r.setBranchPrices(branchPricesMap.getOrDefault(p.getId(), Collections.emptyList()));
            responseMap.put(p.getId(), r);
        }

        enrichGlAccountNames(new java.util.ArrayList<>(responseMap.values()));
        return page.map(p -> responseMap.get(p.getId()));
    }

    /** Batch-load GL account names for a list of product responses. */
    private void enrichGlAccountNames(List<ProductResponse> responses) {
        Set<UUID> accountIds = new java.util.HashSet<>();
        for (ProductResponse r : responses) {
            if (r.getRevenueAccountId() != null) accountIds.add(r.getRevenueAccountId());
            if (r.getCogsAccountId() != null) accountIds.add(r.getCogsAccountId());
        }
        if (accountIds.isEmpty()) return;

        Map<UUID, GlAccount> accountMap = glAccountRepository.findAllById(accountIds).stream()
                .collect(Collectors.toMap(GlAccount::getId, a -> a));
        for (ProductResponse r : responses) {
            r.enrichGlAccountNames(accountMap);
        }
    }
}
