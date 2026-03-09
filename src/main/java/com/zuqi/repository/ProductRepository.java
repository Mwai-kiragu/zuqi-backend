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

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {

    Page<Product> findByActiveTrue(Pageable pageable);

    Page<Product> findByDistributorIdAndActiveTrue(UUID distributorId, Pageable pageable);

    Page<Product> findByActiveFalse(Pageable pageable);

    Page<Product> findByDistributorIdAndActiveFalse(UUID distributorId, Pageable pageable);

    Page<Product> findByCategoryIdAndActiveTrue(Long categoryId, Pageable pageable);

    Optional<Product> findBySkuAndDistributorId(String sku, UUID distributorId);

    boolean existsBySkuAndDistributorId(String sku, UUID distributorId);

    Optional<Product> findByBarcodeAndDistributorId(String barcode, UUID distributorId);

    @Query("SELECT p FROM Product p WHERE p.active = true AND " +
            "(LOWER(p.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(p.sku) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    Page<Product> searchByNameOrSku(@Param("searchTerm") String searchTerm, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.distributor.id = :distributorId AND p.active = true AND " +
            "(LOWER(p.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(p.sku) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    Page<Product> searchByDistributor(
            @Param("distributorId") UUID distributorId,
            @Param("searchTerm") String searchTerm,
            Pageable pageable);

    long countByDistributorIdAndActiveTrue(UUID distributorId);

    long countByCategoryIdAndActiveTrue(Long categoryId);

    List<Product> findByIdIn(List<UUID> ids);

    boolean existsByCategoryId(Long categoryId);

    /**
     * Find all active products for a distributor (for batch operations).
     */
    List<Product> findByDistributorIdAndActiveTrue(UUID distributorId);

    /**
     * Products available for a specific branch:
     * allBranches=true OR has an active entry in product_branch_prices for that branch.
     */
    @Query("SELECT p FROM Product p WHERE p.distributor.id = :distributorId AND p.active = true " +
            "AND (p.allBranches = true OR EXISTS (" +
            "  SELECT pbp FROM ProductBranchPrice pbp " +
            "  WHERE pbp.product = p AND pbp.branch.id = :branchId AND pbp.active = true))")
    Page<Product> findAvailableByDistributorAndBranch(
            @Param("distributorId") UUID distributorId,
            @Param("branchId") UUID branchId,
            Pageable pageable);

    /**
     * Same as above with name/SKU search filter.
     */
    @Query("SELECT p FROM Product p WHERE p.distributor.id = :distributorId AND p.active = true " +
            "AND (LOWER(p.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "     LOWER(p.sku)  LIKE LOWER(CONCAT('%', :searchTerm, '%'))) " +
            "AND (p.allBranches = true OR EXISTS (" +
            "  SELECT pbp FROM ProductBranchPrice pbp " +
            "  WHERE pbp.product = p AND pbp.branch.id = :branchId AND pbp.active = true))")
    Page<Product> searchAvailableByDistributorAndBranch(
            @Param("distributorId") UUID distributorId,
            @Param("branchId") UUID branchId,
            @Param("searchTerm") String searchTerm,
            Pageable pageable);
}
