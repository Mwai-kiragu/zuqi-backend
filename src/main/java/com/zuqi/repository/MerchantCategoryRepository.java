package com.zuqi.repository;

import com.zuqi.domain.merchant.MerchantCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MerchantCategoryRepository extends JpaRepository<MerchantCategory, Long> {

    Optional<MerchantCategory> findByName(String name);

    boolean existsByName(String name);
}
