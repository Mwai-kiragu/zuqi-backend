package com.zuqi.service;

import com.zuqi.api.dto.product.ProductCategoryRequest;
import com.zuqi.api.dto.product.ProductCategoryResponse;
import com.zuqi.api.dto.product.ProductRequest;
import com.zuqi.api.dto.product.ProductResponse;
import com.zuqi.domain.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;


public interface ProductService {

    Page<ProductResponse> getAllProducts(LocalDate startDate, LocalDate endDate, Pageable pageable);

    Page<ProductResponse> getInactiveProducts(Pageable pageable);

    Page<ProductResponse> getProductsByDistributor(UUID distributorId, LocalDate startDate, LocalDate endDate, Pageable pageable);

    /** Same as getProductsByDistributor but excludes variant children (parentProduct IS NULL). */
    Page<ProductResponse> getTopLevelProductsByDistributor(UUID distributorId, LocalDate startDate, LocalDate endDate, Pageable pageable);

    /** Excludes parent templates (hasVariants=true) — returns standalone + variant children only. */
    Page<ProductResponse> getListableProductsByDistributor(UUID distributorId, LocalDate startDate, LocalDate endDate, Pageable pageable);

    Page<ProductResponse> getListableProductsByDistributorAndCategory(UUID distributorId, Long categoryId, LocalDate startDate, LocalDate endDate, Pageable pageable);

    Page<ProductResponse> getInactiveProductsByDistributor(UUID distributorId, Pageable pageable);

    Page<ProductResponse> getProductsByCategory(Long categoryId, Pageable pageable);

    Page<ProductResponse> searchProducts(String searchTerm, UUID distributorId, Pageable pageable);

    /** Search only listable products (active=true, hasVariants=false) — for dropdowns like PR/PO forms. */
    Page<ProductResponse> searchListableProducts(String searchTerm, UUID distributorId, Pageable pageable);

    ProductResponse getProductById(UUID id);

    ProductResponse getProductBySku(String sku, UUID distributorId);

    ProductResponse createProduct(ProductRequest request);

    ProductResponse updateProduct(UUID id, ProductRequest request);

    void deactivateProduct(UUID id, String reason, User currentUser);

    void activateProduct(UUID id);

    List<ProductCategoryResponse> getAllCategories(UUID distributorId, Boolean active, String search, LocalDateTime startDate, LocalDateTime endDate);

    List<ProductCategoryResponse> getInactiveCategories(UUID distributorId);

    ProductCategoryResponse getCategoryById(Long id);

    ProductCategoryResponse createCategory(ProductCategoryRequest request);

    ProductCategoryResponse updateCategory(Long id, ProductCategoryRequest request);

    void deactivateCategory(Long id, String reason, User currentUser);

    void activateCategory(Long id);

    Page<ProductResponse> getProductsForBranch(UUID distributorId, UUID branchId, String search, Long categoryId, Pageable pageable);

    List<ProductResponse> getProductVariants(UUID parentId);

    ProductResponse createProductVariant(UUID parentId, ProductRequest request);
}
