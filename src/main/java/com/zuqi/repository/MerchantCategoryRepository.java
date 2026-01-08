package com.zuqi.repository;

import com.zuqi.domain.merchant.MerchantCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for MerchantCategory entity operations.
 */
@Repository
public interface MerchantCategoryRepository extends JpaRepository<MerchantCategory, Long> {

    /**
     * Find category by name.
     */
    Optional<MerchantCategory> findByName(String name);

    /**
     * Check if category exists by name.
     */
    boolean existsByName(String name);
}
