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
import com.zuqi.exception.ValidationException;
import com.zuqi.api.dto.inventory.StockAdjustmentRequest;
import com.zuqi.domain.inventory.Stock;
import com.zuqi.domain.inventory.StockMovement;
import com.zuqi.domain.inventory.Warehouse;
import com.zuqi.domain.pricing.PriceList;
import com.zuqi.domain.pricing.PriceListItem;
import com.zuqi.domain.approval.ApprovalRequest;
import com.zuqi.domain.approval.ApprovalStatus;
import com.zuqi.repository.ApprovalRequestRepository;
import com.zuqi.repository.ApprovalWorkflowConfigRepository;
import com.zuqi.repository.DistributorBranchRepository;
import com.zuqi.repository.DistributorRepository;
import com.zuqi.repository.GlAccountRepository;
import com.zuqi.repository.PriceListRepository;
import com.zuqi.repository.ProductBranchPriceRepository;
import com.zuqi.repository.ProductCategoryRepository;
import com.zuqi.repository.ProductRepository;
import com.zuqi.repository.StockRepository;
import com.zuqi.repository.WarehouseRepository;
import com.zuqi.domain.audit.ActivityAction;
import com.zuqi.service.ActivityLogService;
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
    private final PriceListRepository priceListRepository;
    private final SecurityUtils securityUtils;
    private final ApprovalService approvalService;
    private final InventoryService inventoryService;
    private final ActivityLogService activityLogService;
    private final ApprovalWorkflowConfigRepository approvalWorkflowConfigRepository;
    private final ApprovalRequestRepository approvalRequestRepository;

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
    public Page<ProductResponse> getTopLevelProductsByDistributor(UUID distributorId, java.time.LocalDate startDate, java.time.LocalDate endDate, Pageable pageable) {
        log.debug("Fetching top-level products for distributor: {}", distributorId);
        boolean hasDates = startDate != null && endDate != null;
        if (hasDates) {
            java.time.LocalDateTime from = startDate.atStartOfDay();
            java.time.LocalDateTime to = endDate.plusDays(1).atStartOfDay();
            return enrichWithStockAndBranchPrices(productRepository.findByDistributorIdAndActiveTrueAndParentProductIsNullAndCreatedAtBetween(distributorId, from, to, pageable));
        }
        return enrichWithStockAndBranchPrices(productRepository.findByDistributorIdAndActiveTrueAndParentProductIsNull(distributorId, pageable));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductResponse> getListableProductsByDistributor(UUID distributorId, java.time.LocalDate startDate, java.time.LocalDate endDate, Pageable pageable) {
        log.debug("Fetching listable (non-template) products for distributor: {}", distributorId);
        boolean hasDates = startDate != null && endDate != null;
        if (hasDates) {
            java.time.LocalDateTime from = startDate.atStartOfDay();
            java.time.LocalDateTime to = endDate.plusDays(1).atStartOfDay();
            return enrichWithStockAndBranchPrices(productRepository.findByDistributorIdAndActiveTrueAndHasVariantsFalseAndCreatedAtBetween(distributorId, from, to, pageable));
        }
        return enrichWithStockAndBranchPrices(productRepository.findByDistributorIdAndActiveTrueAndHasVariantsFalse(distributorId, pageable));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductResponse> getListableProductsByDistributorAndCategory(UUID distributorId, Long categoryId, java.time.LocalDate startDate, java.time.LocalDate endDate, Pageable pageable) {
        log.debug("Fetching listable products for distributor: {}, category: {}", distributorId, categoryId);
        boolean hasDates = startDate != null && endDate != null;
        if (hasDates) {
            java.time.LocalDateTime from = startDate.atStartOfDay();
            java.time.LocalDateTime to = endDate.plusDays(1).atStartOfDay();
            return enrichWithStockAndBranchPrices(productRepository.findByDistributorIdAndCategoryIdAndActiveTrueAndHasVariantsFalseAndCreatedAtBetween(distributorId, categoryId, from, to, pageable));
        }
        return enrichWithStockAndBranchPrices(productRepository.findByDistributorIdAndCategoryIdAndActiveTrueAndHasVariantsFalse(distributorId, categoryId, pageable));
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
    public Page<ProductResponse> searchListableProducts(String searchTerm, UUID distributorId, Pageable pageable) {
        log.debug("Searching listable products with term: {}, distributor: {}", searchTerm, distributorId);
        UUID effectiveDistributorId = distributorId != null ? distributorId : securityUtils.getDistributorIdForFiltering();
        if (effectiveDistributorId != null) {
            return enrichWithStockAndBranchPrices(productRepository.searchListableByDistributor(effectiveDistributorId, searchTerm, pageable));
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
        // Embed variants for parent products
        if (product.isHasVariants()) {
            List<ProductResponse> variants = productRepository.findByParentProductIdAndActiveTrue(id).stream()
                    .map(ProductResponse::fromEntity)
                    .collect(Collectors.toList());
            response.setVariants(variants);
        }
        enrichGlAccountNames(List.of(response));
        enrichPendingPriceApproval(response, id);
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
                .hasVariants(request.isHasVariants())
                .variantName(request.getVariantName())
                .variantAttributes(request.getVariantAttributes())
                .build();

        // Link to parent product if this is a variant
        if (request.getParentProductId() != null) {
            Product parent = productRepository.findById(request.getParentProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product", "id", request.getParentProductId().toString()));
            if (!parent.isHasVariants()) {
                throw new ValidationException("Parent product is not configured as a variant parent.");
            }
            product.setParentProduct(parent);
        }

        if (request.getCategoryId() != null) {
            ProductCategory category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("ProductCategory", "id", request.getCategoryId().toString()));
            product.setCategory(category);
        }

        boolean needsApproval = securityUtils.currentUserRequiresApprovalFor("PRODUCTS");
        // FINANCE role must always go through approval even if requiresApproval flag is not set on their UserType
        if (!needsApproval && securityUtils.currentUserHasRole("FINANCE")) needsApproval = true;
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

        // Ensure a zero-stock record exists in every active warehouse so the product appears in inventory
        if (!savedProduct.isHasVariants()) {
            List<Warehouse> allWarehouses = warehouseRepository.findByDistributorIdAndActiveTrue(savedProduct.getDistributor().getId());
            for (Warehouse wh : allWarehouses) {
                if (!stockRepository.existsByWarehouseIdAndProductId(wh.getId(), savedProduct.getId())) {
                    stockRepository.save(Stock.builder()
                            .warehouse(wh)
                            .product(savedProduct)
                            .quantity(BigDecimal.ZERO)
                            .reservedQuantity(BigDecimal.ZERO)
                            .build());
                }
            }
        }

        // Auto-add product to the distributor's default price list
        addToDefaultPriceList(savedProduct);

        User currentUser = securityUtils.getCurrentUser();
        if (currentUser != null) {
            activityLogService.log(
                currentUser.getId(), currentUser.getEmail(),
                currentUser.getFirstName() + " " + currentUser.getLastName(),
                ActivityAction.CREATE, "PRODUCT", savedProduct.getId(),
                savedProduct.getName(), "PRODUCTS", "Created product: " + savedProduct.getName()
            );
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

    private void addToDefaultPriceList(Product product) {
        try {
            // Skip parent templates — only add directly-sellable products (standalone or variants)
            if (product.isHasVariants()) return;
            Distributor distributor = product.getDistributor();
            if (distributor == null) return;

            PriceList priceList = priceListRepository
                    .findByDistributorIdAndIsDefaultTrue(distributor.getId())
                    .orElseGet(() -> {
                        PriceList newList = PriceList.builder()
                                .distributor(distributor)
                                .name("Standard Price List")
                                .isDefault(true)
                                .active(true)
                                .approvalStatus("APPROVED")
                                .build();
                        return priceListRepository.save(newList);
                    });

            boolean alreadyExists = priceList.getItems().stream()
                    .anyMatch(i -> i.getProduct().getId().equals(product.getId()));
            if (alreadyExists) return;

            BigDecimal price = product.getUnitPrice() != null ? product.getUnitPrice() : BigDecimal.ZERO;
            PriceListItem item = PriceListItem.builder()
                    .priceList(priceList)
                    .product(product)
                    .unitPrice(price)
                    .discountPercent(BigDecimal.ZERO)
                    .build();
            priceList.getItems().add(item);
            priceListRepository.save(priceList);
            log.info("Auto-added product {} to default price list {}", product.getId(), priceList.getId());
        } catch (Exception e) {
            log.warn("Could not auto-add product {} to default price list: {}", product.getId(), e.getMessage());
        }
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

        // Detect price changes before applying anything
        BigDecimal currentUnitPrice = product.getUnitPrice();
        BigDecimal currentCostPrice = product.getCostPrice();
        BigDecimal newUnitPrice = request.getUnitPrice();
        BigDecimal newCostPrice = request.getCostPrice();

        boolean unitPriceChanged = newUnitPrice != null && newUnitPrice.compareTo(currentUnitPrice != null ? currentUnitPrice : BigDecimal.ZERO) != 0;
        boolean costPriceChanged = newCostPrice != null && currentCostPrice != null && newCostPrice.compareTo(currentCostPrice) != 0;
        boolean priceChanged = unitPriceChanged || costPriceChanged;

        // Check if a PRODUCT_PRICE_EDIT workflow is configured for this distributor
        UUID distributorId = product.getDistributor() != null ? product.getDistributor().getId() : null;
        boolean priceWorkflowActive = priceChanged && distributorId != null &&
                !approvalWorkflowConfigRepository.findByDistributorIdAndWorkflowTypeAndActiveTrueOrderByLevelNumberAsc(
                        distributorId, ApprovalWorkflowType.PRODUCT_PRICE_EDIT).isEmpty();

        product.setSku(request.getSku());
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setAllBranches(request.isAllBranches());

        // Only apply price changes now if no approval workflow is configured
        if (!priceWorkflowActive) {
            product.setUnitPrice(newUnitPrice);
            if (newCostPrice != null) product.setCostPrice(newCostPrice);
        }

        if (request.getUnitOfMeasure() != null) {
            product.setUnitOfMeasure(request.getUnitOfMeasure());
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

        // Create price-change approval request if workflow is active
        UUID pendingApprovalId = null;
        if (priceWorkflowActive) {
            UUID currentUserId = securityUtils.getCurrentUserId();
            if (currentUserId != null) {
                Map<String, Object> currentVals = new java.util.LinkedHashMap<>();
                if (currentUnitPrice != null) currentVals.put("unitPrice", currentUnitPrice.toPlainString());
                if (currentCostPrice != null) currentVals.put("costPrice", currentCostPrice.toPlainString());

                Map<String, Object> requestedVals = new java.util.LinkedHashMap<>();
                if (unitPriceChanged) requestedVals.put("unitPrice", newUnitPrice.toPlainString());
                if (costPriceChanged) requestedVals.put("costPrice", newCostPrice.toPlainString());

                var approvalResponse = approvalService.createRequest(currentUserId, CreateApprovalRequestDto.builder()
                        .workflowType(ApprovalWorkflowType.PRODUCT_PRICE_EDIT)
                        .entityType("PRODUCT_PRICE_CHANGE")
                        .entityId(updatedProduct.getId())
                        .entityName(updatedProduct.getName())
                        .description("Price change for: " + updatedProduct.getName())
                        .currentValues(currentVals)
                        .requestedValues(requestedVals)
                        .requiredApprovals(1)
                        .build());
                pendingApprovalId = approvalResponse.getId();
                log.info("Created price-change approval request {} for product {}", approvalResponse.getRequestNumber(), updatedProduct.getName());
            }
        }

        User currentUser = securityUtils.getCurrentUser();
        if (currentUser != null) {
            activityLogService.log(
                currentUser.getId(), currentUser.getEmail(),
                currentUser.getFirstName() + " " + currentUser.getLastName(),
                ActivityAction.UPDATE, "PRODUCT", updatedProduct.getId(),
                updatedProduct.getName(), "PRODUCTS", "Updated product: " + updatedProduct.getName()
            );
        }

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

        // Attach pending price approval info so the frontend can show a banner
        if (pendingApprovalId != null) {
            response.setPendingPriceApprovalId(pendingApprovalId);
            response.setPendingUnitPrice(unitPriceChanged ? newUnitPrice : null);
            response.setPendingCostPrice(costPriceChanged ? newCostPrice : null);
        } else {
            // Also populate from any pre-existing pending approval (e.g. on GET)
            enrichPendingPriceApproval(response, updatedProduct.getId());
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
        if (currentUser != null) {
            activityLogService.log(
                currentUser.getId(), currentUser.getEmail(),
                currentUser.getFirstName() + " " + currentUser.getLastName(),
                ActivityAction.DEACTIVATE, "PRODUCT", product.getId(),
                product.getName(), "PRODUCTS", "Deactivated product: " + product.getName()
            );
        }
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
        User currentUser = securityUtils.getCurrentUser();
        if (currentUser != null) {
            activityLogService.log(
                currentUser.getId(), currentUser.getEmail(),
                currentUser.getFirstName() + " " + currentUser.getLastName(),
                ActivityAction.ACTIVATE, "PRODUCT", product.getId(),
                product.getName(), "PRODUCTS", "Activated product: " + product.getName()
            );
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductCategoryResponse> getAllCategories(UUID distributorId, Boolean active, String search, java.time.LocalDateTime startDate, java.time.LocalDateTime endDate) {
        log.debug("Fetching product categories for distributor: {}, active: {}, search: {}", distributorId, active, search);

        String searchParam = (search != null && !search.isBlank()) ? search.trim() : null;

        UUID effectiveDistributorId = distributorId;
        if (effectiveDistributorId == null) {
            UUID merchantId = securityUtils.getCurrentUserMerchantId();
            if (merchantId != null) {
                return categoryRepository.findFilteredByMerchant(merchantId, active, searchParam, startDate, endDate).stream()
                        .map(ProductCategoryResponse::fromEntity)
                        .toList();
            }
            effectiveDistributorId = securityUtils.getDistributorIdForFiltering();
        }

        if (effectiveDistributorId != null) {
            return categoryRepository.findFiltered(effectiveDistributorId, active, searchParam, startDate, endDate).stream()
                    .map(ProductCategoryResponse::fromEntity)
                    .toList();
        }

        return categoryRepository.findFilteredAll(active, searchParam, startDate, endDate).stream()
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

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponse> getProductVariants(UUID parentId) {
        productRepository.findById(parentId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", parentId.toString()));
        return productRepository.findByParentProductIdAndActiveTrue(parentId).stream()
                .map(ProductResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ProductResponse createProductVariant(UUID parentId, ProductRequest request) {
        log.info("Creating variant for parent product: {}", parentId);

        Product parent = productRepository.findById(parentId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", parentId.toString()));

        if (!parent.isHasVariants()) {
            throw new ValidationException("Product " + parentId + " is not configured as a parent product. Set hasVariants=true first.");
        }

        if (productRepository.existsBySkuAndDistributorId(request.getSku(), parent.getDistributor().getId())) {
            throw new DuplicateResourceException("Product", "sku", request.getSku());
        }

        String variantDisplayName = request.getName() != null
                ? request.getName()
                : parent.getName() + (request.getVariantName() != null ? " - " + request.getVariantName() : "");

        Product variant = Product.builder()
                .distributor(parent.getDistributor())
                .sku(request.getSku())
                .name(variantDisplayName)
                .description(request.getDescription() != null ? request.getDescription() : parent.getDescription())
                .unitOfMeasure(request.getUnitOfMeasure() != null ? request.getUnitOfMeasure() : parent.getUnitOfMeasure())
                .unitPrice(request.getUnitPrice())
                .costPrice(request.getCostPrice() != null ? request.getCostPrice() : parent.getCostPrice())
                .imageUrl(request.getImageUrl() != null ? request.getImageUrl() : parent.getImageUrl())
                .barcode(request.getBarcode())
                .allBranches(parent.isAllBranches())
                .revenueAccountId(request.getRevenueAccountId() != null ? request.getRevenueAccountId() : parent.getRevenueAccountId())
                .cogsAccountId(request.getCogsAccountId() != null ? request.getCogsAccountId() : parent.getCogsAccountId())
                .minSalePrice(request.getMinSalePrice() != null ? request.getMinSalePrice() : parent.getMinSalePrice())
                .hasVariants(false)
                .parentProduct(parent)
                .variantName(request.getVariantName())
                .variantAttributes(request.getVariantAttributes())
                .createdById(securityUtils.getCurrentUserId())
                .build();

        boolean needsApproval = securityUtils.currentUserRequiresApprovalFor("PRODUCTS");
        variant.setApprovalStatus(needsApproval ? "PENDING_APPROVAL" : "APPROVED");

        if (request.getCategoryId() != null) {
            ProductCategory category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("ProductCategory", "id", request.getCategoryId().toString()));
            variant.setCategory(category);
        } else if (parent.getCategory() != null) {
            variant.setCategory(parent.getCategory());
        }

        UUID currentUserId = securityUtils.getCurrentUserId();
        Product saved = productRepository.save(variant);

        if (needsApproval && currentUserId != null) {
            approvalService.createRequest(currentUserId, CreateApprovalRequestDto.builder()
                    .workflowType(ApprovalWorkflowType.PRODUCT_PRICE_EDIT)
                    .entityType("PRODUCT")
                    .entityId(saved.getId())
                    .entityName(saved.getName())
                    .description("New product variant: " + saved.getName())
                    .requestedValues(Map.of(
                            "name", saved.getName(),
                            "sku", saved.getSku(),
                            "unitPrice", Objects.toString(saved.getUnitPrice(), "")))
                    .requiredApprovals(1)
                    .build());
        }

        addToDefaultPriceList(saved);
        log.info("Variant created successfully: {}", saved.getId());
        return ProductResponse.fromEntity(saved);
    }

    // -------------------------------------------------------
    // Helpers
    // -------------------------------------------------------

    private void enrichPendingPriceApproval(ProductResponse response, UUID productId) {
        List<ApprovalRequest> pending = approvalRequestRepository
                .findByEntityTypeAndEntityIdAndStatus("PRODUCT_PRICE_CHANGE", productId, ApprovalStatus.PENDING);
        if (!pending.isEmpty()) {
            ApprovalRequest req = pending.get(0);
            response.setPendingPriceApprovalId(req.getId());
            Map<String, Object> vals = req.getRequestedValues();
            if (vals != null) {
                if (vals.containsKey("unitPrice")) {
                    try { response.setPendingUnitPrice(new java.math.BigDecimal(vals.get("unitPrice").toString())); } catch (Exception ignored) {}
                }
                if (vals.containsKey("costPrice")) {
                    try { response.setPendingCostPrice(new java.math.BigDecimal(vals.get("costPrice").toString())); } catch (Exception ignored) {}
                }
            }
        }
    }

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
