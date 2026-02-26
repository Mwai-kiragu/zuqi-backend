package com.zuqi.repository;

import com.zuqi.domain.supplier.SupplierCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SupplierCategoryRepository extends JpaRepository<SupplierCategory, Long> {
    boolean existsByName(String name);
}
