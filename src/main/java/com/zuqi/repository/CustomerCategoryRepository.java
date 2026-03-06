package com.zuqi.repository;

import com.zuqi.domain.customer.CustomerCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CustomerCategoryRepository extends JpaRepository<CustomerCategory, Long> {

    Optional<CustomerCategory> findByName(String name);

    boolean existsByName(String name);
}
