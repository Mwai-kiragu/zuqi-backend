package com.zuqi.repository;

import com.zuqi.domain.product.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Product entity operations.
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {

    /**
     * Find all active products.
     */
    Page<Product> findByActiveTrue(Pageable pageable);

    /**
     * Find products by distributor.
     */
    Page<Product> findByDistributorIdAndActiveTrue(UUID distributorId, Pageable pageable);

    /**
     * Find all inactive products.
     */
    Page<Product> findByActiveFalse(Pageable pageable);

    /**
     * Find inactive products by distributor.
     */
    Page<Product> findByDistributorIdAndActiveFalse(UUID distributorId, Pageable pageable);

    /**
     * Find products by category.
     */
    Page<Product> findByCategoryIdAndActiveTrue(Long categoryId, Pageable pageable);

    /**
     * Find product by SKU and distributor.
     */
    Optional<Product> findBySkuAndDistributorId(String sku, UUID distributorId);

    /**
     * Check if product exists by SKU and distributor.
     */
    boolean existsBySkuAndDistributorId(String sku, UUID distributorId);

    /**
     * Find product by barcode.
     */
    Optional<Product> findByBarcodeAndDistributorId(String barcode, UUID distributorId);

    /**
     * Search products by name or SKU.
     */
    @Query("SELECT p FROM Product p WHERE p.active = true AND " +
            "(LOWER(p.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(p.sku) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    Page<Product> searchByNameOrSku(@Param("searchTerm") String searchTerm, Pageable pageable);

    /**
     * Search products by distributor.
     */
    @Query("SELECT p FROM Product p WHERE p.distributor.id = :distributorId AND p.active = true AND " +
            "(LOWER(p.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(p.sku) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    Page<Product> searchByDistributor(
            @Param("distributorId") UUID distributorId,
            @Param("searchTerm") String searchTerm,
            Pageable pageable);

    /**
     * Count products by distributor.
     */
    long countByDistributorIdAndActiveTrue(UUID distributorId);

    /**
     * Count products by category.
     */
    long countByCategoryIdAndActiveTrue(Long categoryId);

    /**
     * Find products by IDs.
     */
    List<Product> findByIdIn(List<UUID> ids);

    /**
     * Check if products exist for a category.
     */
    boolean existsByCategoryId(Long categoryId);
}
