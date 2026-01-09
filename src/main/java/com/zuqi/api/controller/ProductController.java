package com.zuqi.api.controller;

import com.zuqi.api.dto.ApiResponse;
import com.zuqi.api.dto.common.DeactivateRequest;
import com.zuqi.api.dto.product.ProductCategoryRequest;
import com.zuqi.api.dto.product.ProductCategoryResponse;
import com.zuqi.api.dto.product.ProductRequest;
import com.zuqi.api.dto.product.ProductResponse;
import com.zuqi.domain.user.User;
import com.zuqi.service.ProductService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for product operations.
 */
@RestController
@RequestMapping("/v1/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "Product management APIs")
public class ProductController {

    private final ProductService productService;

    /**
     * Get all products with pagination and optional filters.
     */
    @GetMapping
    @Operation(summary = "Get all products", description = "Retrieves products with pagination and optional filters")
    public ResponseEntity<ApiResponse<Page<ProductResponse>>> getAllProducts(
            @Parameter(description = "Distributor ID filter") @RequestParam(required = false) UUID distributorId,
            @Parameter(description = "Category ID filter") @RequestParam(required = false) Long categoryId,
            @Parameter(description = "Search term") @RequestParam(required = false) String search,
            @Parameter(description = "Filter by active status") @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {

        Page<ProductResponse> products;

        if (search != null && !search.isBlank()) {
            products = productService.searchProducts(search, distributorId, pageable);
        } else if (active != null && !active) {
            // Return inactive products
            if (distributorId != null) {
                products = productService.getInactiveProductsByDistributor(distributorId, pageable);
            } else {
                products = productService.getInactiveProducts(pageable);
            }
        } else if (distributorId != null) {
            products = productService.getProductsByDistributor(distributorId, pageable);
        } else if (categoryId != null) {
            products = productService.getProductsByCategory(categoryId, pageable);
        } else {
            products = productService.getAllProducts(pageable);
        }

        return ResponseEntity.ok(ApiResponse.success(products));
    }

    /**
     * Get a product by ID.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get product by ID", description = "Retrieves a specific product by ID")
    public ResponseEntity<ApiResponse<ProductResponse>> getProductById(
            @Parameter(description = "Product ID") @PathVariable UUID id) {
        ProductResponse product = productService.getProductById(id);
        return ResponseEntity.ok(ApiResponse.success(product));
    }

    /**
     * Get a product by SKU.
     */
    @GetMapping("/sku/{sku}")
    @Operation(summary = "Get product by SKU", description = "Retrieves a product by SKU for a distributor")
    public ResponseEntity<ApiResponse<ProductResponse>> getProductBySku(
            @Parameter(description = "Product SKU") @PathVariable String sku,
            @Parameter(description = "Distributor ID") @RequestParam UUID distributorId) {
        ProductResponse product = productService.getProductBySku(sku, distributorId);
        return ResponseEntity.ok(ApiResponse.success(product));
    }

    /**
     * Create a new product.
     */
    @PostMapping
    @Operation(summary = "Create product", description = "Creates a new product")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'DISTRIBUTOR_ADMIN')")
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(
            @Valid @RequestBody ProductRequest request) {
        ProductResponse product = productService.createProduct(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Product created successfully", product));
    }

    /**
     * Update an existing product.
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update product", description = "Updates an existing product")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'DISTRIBUTOR_ADMIN')")
    public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(
            @Parameter(description = "Product ID") @PathVariable UUID id,
            @Valid @RequestBody ProductRequest request) {
        ProductResponse product = productService.updateProduct(id, request);
        return ResponseEntity.ok(ApiResponse.success("Product updated successfully", product));
    }

    /**
     * Deactivate a product with reason.
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Deactivate product", description = "Deactivates a product (soft delete) with reason")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'DISTRIBUTOR_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deactivateProduct(
            @Parameter(description = "Product ID") @PathVariable UUID id,
            @Valid @RequestBody DeactivateRequest request,
            @AuthenticationPrincipal User currentUser) {
        productService.deactivateProduct(id, request.getReason(), currentUser);
        return ResponseEntity.ok(ApiResponse.success("Product deactivated successfully"));
    }

    /**
     * Activate a product.
     */
    @PostMapping("/{id}/activate")
    @Operation(summary = "Activate product", description = "Reactivates a deactivated product")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'DISTRIBUTOR_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> activateProduct(
            @Parameter(description = "Product ID") @PathVariable UUID id) {
        productService.activateProduct(id);
        return ResponseEntity.ok(ApiResponse.success("Product activated successfully"));
    }

    /**
     * Get all product categories.
     */
    @GetMapping("/categories")
    @Operation(summary = "Get product categories", description = "Retrieves all product categories")
    public ResponseEntity<ApiResponse<List<ProductCategoryResponse>>> getCategories(
            @Parameter(description = "Distributor ID filter") @RequestParam(required = false) UUID distributorId) {
        List<ProductCategoryResponse> categories = productService.getAllCategories(distributorId);
        return ResponseEntity.ok(ApiResponse.success(categories));
    }

    /**
     * Get a product category by ID.
     */
    @GetMapping("/categories/{id}")
    @Operation(summary = "Get category by ID", description = "Retrieves a specific product category by ID")
    public ResponseEntity<ApiResponse<ProductCategoryResponse>> getCategoryById(
            @Parameter(description = "Category ID") @PathVariable Long id) {
        ProductCategoryResponse category = productService.getCategoryById(id);
        return ResponseEntity.ok(ApiResponse.success(category));
    }

    /**
     * Create a new product category.
     */
    @PostMapping("/categories")
    @Operation(summary = "Create category", description = "Creates a new product category")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'DISTRIBUTOR_ADMIN')")
    public ResponseEntity<ApiResponse<ProductCategoryResponse>> createCategory(
            @Valid @RequestBody ProductCategoryRequest request) {
        ProductCategoryResponse category = productService.createCategory(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Category created successfully", category));
    }

    /**
     * Update an existing product category.
     */
    @PutMapping("/categories/{id}")
    @Operation(summary = "Update category", description = "Updates an existing product category")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'DISTRIBUTOR_ADMIN')")
    public ResponseEntity<ApiResponse<ProductCategoryResponse>> updateCategory(
            @Parameter(description = "Category ID") @PathVariable Long id,
            @Valid @RequestBody ProductCategoryRequest request) {
        ProductCategoryResponse category = productService.updateCategory(id, request);
        return ResponseEntity.ok(ApiResponse.success("Category updated successfully", category));
    }

    /**
     * Deactivate a product category with reason.
     */
    @DeleteMapping("/categories/{id}")
    @Operation(summary = "Deactivate category", description = "Deactivates a product category (soft delete) with reason")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'DISTRIBUTOR_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deactivateCategory(
            @Parameter(description = "Category ID") @PathVariable Long id,
            @Valid @RequestBody DeactivateRequest request,
            @AuthenticationPrincipal User currentUser) {
        productService.deactivateCategory(id, request.getReason(), currentUser);
        return ResponseEntity.ok(ApiResponse.success("Category deactivated successfully"));
    }

    /**
     * Activate a product category.
     */
    @PostMapping("/categories/{id}/activate")
    @Operation(summary = "Activate category", description = "Reactivates a deactivated product category")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'DISTRIBUTOR_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> activateCategory(
            @Parameter(description = "Category ID") @PathVariable Long id) {
        productService.activateCategory(id);
        return ResponseEntity.ok(ApiResponse.success("Category activated successfully"));
    }
}
