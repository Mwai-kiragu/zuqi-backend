package com.zuqi.repository;

import com.zuqi.domain.product.ProductCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductCategoryRepository extends JpaRepository<ProductCategory, Long> {

    List<ProductCategory> findByDistributorIdAndActiveTrue(UUID distributorId);

    /** Scope to a merchant brand (MERCHANT_ADMIN). */
    List<ProductCategory> findByDistributorMerchantIdAndActiveTrue(UUID merchantId);

    List<ProductCategory> findByDistributorMerchantIdAndActiveFalse(UUID merchantId);

    List<ProductCategory> findByDistributorIdAndActiveFalse(UUID distributorId);

    List<ProductCategory> findByDistributorId(UUID distributorId);

    List<ProductCategory> findByDistributorIdAndParentIsNull(UUID distributorId);

    List<ProductCategory> findByDistributorIdAndParentIsNullAndActiveTrue(UUID distributorId);

    Optional<ProductCategory> findByNameAndDistributorId(String name, UUID distributorId);

    boolean existsByNameAndDistributorId(String name, UUID distributorId);

    List<ProductCategory> findByParentId(Long parentId);

    List<ProductCategory> findByParentIdAndActiveTrue(Long parentId);
}
