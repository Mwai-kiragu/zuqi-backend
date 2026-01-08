package com.zuqi.service.impl;

import com.zuqi.api.dto.product.ProductCategoryRequest;
import com.zuqi.api.dto.product.ProductCategoryResponse;
import com.zuqi.api.dto.product.ProductRequest;
import com.zuqi.api.dto.product.ProductResponse;
import com.zuqi.domain.distributor.Distributor;
import com.zuqi.domain.product.Product;
import com.zuqi.domain.product.ProductCategory;
import com.zuqi.exception.DuplicateResourceException;
import com.zuqi.exception.ResourceNotFoundException;
import com.zuqi.repository.DistributorRepository;
import com.zuqi.repository.ProductCategoryRepository;
import com.zuqi.repository.ProductRepository;
import com.zuqi.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Implementation of the product service.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductCategoryRepository categoryRepository;
    private final DistributorRepository distributorRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<ProductResponse> getAllProducts(Pageable pageable) {
        log.debug("Fetching all products");
        return productRepository.findByActiveTrue(pageable)
                .map(ProductResponse::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductResponse> getProductsByDistributor(UUID distributorId, Pageable pageable) {
        log.debug("Fetching products for distributor: {}", distributorId);
        return productRepository.findByDistributorIdAndActiveTrue(distributorId, pageable)
                .map(ProductResponse::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductResponse> getProductsByCategory(Long categoryId, Pageable pageable) {
        log.debug("Fetching products for category: {}", categoryId);
        return productRepository.findByCategoryIdAndActiveTrue(categoryId, pageable)
                .map(ProductResponse::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductResponse> searchProducts(String searchTerm, UUID distributorId, Pageable pageable) {
        log.debug("Searching products with term: {}, distributor: {}", searchTerm, distributorId);
        if (distributorId != null) {
            return productRepository.searchByDistributor(distributorId, searchTerm, pageable)
                    .map(ProductResponse::fromEntity);
        }
        return productRepository.searchByNameOrSku(searchTerm, pageable)
                .map(ProductResponse::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getProductById(UUID id) {
        log.debug("Fetching product by ID: {}", id);
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id.toString()));
        return ProductResponse.fromEntity(product);
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

        // Get distributor
        Distributor distributor = distributorRepository.findById(request.getDistributorId())
                .orElseThrow(() -> new ResourceNotFoundException("Distributor", "id", request.getDistributorId().toString()));

        // Check for duplicate SKU
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
                .taxRate(request.getTaxRate() != null ? request.getTaxRate() : BigDecimal.ZERO)
                .imageUrl(request.getImageUrl())
                .barcode(request.getBarcode())
                .build();

        // Set category if provided
        if (request.getCategoryId() != null) {
            ProductCategory category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("ProductCategory", "id", request.getCategoryId().toString()));
            product.setCategory(category);
        }

        Product savedProduct = productRepository.save(product);
        log.info("Product created successfully with ID: {}", savedProduct.getId());

        return ProductResponse.fromEntity(savedProduct);
    }

    @Override
    @Transactional
    public ProductResponse updateProduct(UUID id, ProductRequest request) {
        log.info("Updating product: {}", id);

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id.toString()));

        // Check for duplicate SKU if changed
        if (!product.getSku().equals(request.getSku()) &&
                productRepository.existsBySkuAndDistributorId(request.getSku(), product.getDistributor().getId())) {
            throw new DuplicateResourceException("Product", "sku", request.getSku());
        }

        product.setSku(request.getSku());
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setUnitPrice(request.getUnitPrice());

        if (request.getUnitOfMeasure() != null) {
            product.setUnitOfMeasure(request.getUnitOfMeasure());
        }
        if (request.getCostPrice() != null) {
            product.setCostPrice(request.getCostPrice());
        }
        if (request.getTaxRate() != null) {
            product.setTaxRate(request.getTaxRate());
        }
        if (request.getImageUrl() != null) {
            product.setImageUrl(request.getImageUrl());
        }
        if (request.getBarcode() != null) {
            product.setBarcode(request.getBarcode());
        }

        // Update category if provided
        if (request.getCategoryId() != null) {
            ProductCategory category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("ProductCategory", "id", request.getCategoryId().toString()));
            product.setCategory(category);
        }

        Product updatedProduct = productRepository.save(product);
        log.info("Product updated successfully: {}", id);

        return ProductResponse.fromEntity(updatedProduct);
    }

    @Override
    @Transactional
    public void deactivateProduct(UUID id) {
        log.info("Deactivating product: {}", id);

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id.toString()));

        product.setActive(false);
        productRepository.save(product);

        log.info("Product deactivated successfully");
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductCategoryResponse> getAllCategories(UUID distributorId) {
        log.debug("Fetching product categories for distributor: {}", distributorId);
        if (distributorId != null) {
            return categoryRepository.findByDistributorId(distributorId).stream()
                    .map(ProductCategoryResponse::fromEntity)
                    .toList();
        }
        return categoryRepository.findAll().stream()
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

        // Get distributor
        Distributor distributor = distributorRepository.findById(request.getDistributorId())
                .orElseThrow(() -> new ResourceNotFoundException("Distributor", "id", request.getDistributorId().toString()));

        // Check for duplicate name
        if (categoryRepository.existsByNameAndDistributorId(request.getName(), request.getDistributorId())) {
            throw new DuplicateResourceException("ProductCategory", "name", request.getName());
        }

        ProductCategory category = ProductCategory.builder()
                .name(request.getName())
                .description(request.getDescription())
                .distributor(distributor)
                .build();

        // Set parent if provided
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

        // Check for duplicate name if changed
        if (!category.getName().equals(request.getName()) &&
                categoryRepository.existsByNameAndDistributorId(request.getName(), category.getDistributor().getId())) {
            throw new DuplicateResourceException("ProductCategory", "name", request.getName());
        }

        category.setName(request.getName());
        category.setDescription(request.getDescription());

        // Update parent if provided
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
    public void deleteCategory(Long id) {
        log.info("Deleting product category: {}", id);

        ProductCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ProductCategory", "id", id.toString()));

        // Check if category has child categories
        List<ProductCategory> children = categoryRepository.findByParentId(id);
        if (!children.isEmpty()) {
            throw new IllegalStateException("Cannot delete category with child categories");
        }

        // Check if category has products
        if (productRepository.existsByCategoryId(id)) {
            throw new IllegalStateException("Cannot delete category with associated products");
        }

        categoryRepository.delete(category);
        log.info("Product category deleted successfully: {}", id);
    }
}
