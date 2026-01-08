package com.zuqi.repository;

import com.zuqi.domain.product.ProductCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for ProductCategory entity operations.
 */
@Repository
public interface ProductCategoryRepository extends JpaRepository<ProductCategory, Long> {

    /**
     * Find categories by distributor.
     */
    List<ProductCategory> findByDistributorId(UUID distributorId);

    /**
     * Find top-level categories (no parent) by distributor.
     */
    List<ProductCategory> findByDistributorIdAndParentIsNull(UUID distributorId);

    /**
     * Find category by name and distributor.
     */
    Optional<ProductCategory> findByNameAndDistributorId(String name, UUID distributorId);

    /**
     * Check if category exists by name and distributor.
     */
    boolean existsByNameAndDistributorId(String name, UUID distributorId);

    /**
     * Find child categories.
     */
    List<ProductCategory> findByParentId(Long parentId);
}
