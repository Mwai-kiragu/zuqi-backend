package com.zuqi.service;

import com.zuqi.api.dto.product.ProductCategoryRequest;
import com.zuqi.api.dto.product.ProductCategoryResponse;
import com.zuqi.api.dto.product.ProductRequest;
import com.zuqi.api.dto.product.ProductResponse;
import com.zuqi.domain.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

/**
 * Service interface for product operations.
 */
public interface ProductService {

    /**
     * Get all products with pagination.
     */
    Page<ProductResponse> getAllProducts(Pageable pageable);

    /**
     * Get all inactive products with pagination.
     */
    Page<ProductResponse> getInactiveProducts(Pageable pageable);

    /**
     * Get products by distributor with pagination.
     */
    Page<ProductResponse> getProductsByDistributor(UUID distributorId, Pageable pageable);

    /**
     * Get inactive products by distributor with pagination.
     */
    Page<ProductResponse> getInactiveProductsByDistributor(UUID distributorId, Pageable pageable);

    /**
     * Get products by category.
     */
    Page<ProductResponse> getProductsByCategory(Long categoryId, Pageable pageable);

    /**
     * Search products.
     */
    Page<ProductResponse> searchProducts(String searchTerm, UUID distributorId, Pageable pageable);

    /**
     * Get a product by ID.
     */
    ProductResponse getProductById(UUID id);

    /**
     * Get a product by SKU.
     */
    ProductResponse getProductBySku(String sku, UUID distributorId);

    /**
     * Create a new product.
     */
    ProductResponse createProduct(ProductRequest request);

    /**
     * Update an existing product.
     */
    ProductResponse updateProduct(UUID id, ProductRequest request);

    /**
     * Deactivate a product with reason.
     */
    void deactivateProduct(UUID id, String reason, User currentUser);

    /**
     * Activate a product.
     */
    void activateProduct(UUID id);

    /**
     * Get all active product categories.
     */
    List<ProductCategoryResponse> getAllCategories(UUID distributorId);

    /**
     * Get all inactive product categories.
     */
    List<ProductCategoryResponse> getInactiveCategories(UUID distributorId);

    /**
     * Get a product category by ID.
     */
    ProductCategoryResponse getCategoryById(Long id);

    /**
     * Create a new product category.
     */
    ProductCategoryResponse createCategory(ProductCategoryRequest request);

    /**
     * Update an existing product category.
     */
    ProductCategoryResponse updateCategory(Long id, ProductCategoryRequest request);

    /**
     * Deactivate a product category with reason.
     */
    void deactivateCategory(Long id, String reason, User currentUser);

    /**
     * Activate a product category.
     */
    void activateCategory(Long id);
}
