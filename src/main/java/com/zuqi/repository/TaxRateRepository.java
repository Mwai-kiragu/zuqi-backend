package com.zuqi.repository;

import com.zuqi.domain.accounting.TaxRate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaxRateRepository extends JpaRepository<TaxRate, UUID> {
    Page<TaxRate> findByDistributorIdOrderByNameAsc(UUID distributorId, Pageable pageable);
    List<TaxRate> findByDistributorIdAndActiveOrderByNameAsc(UUID distributorId, boolean active);
    List<TaxRate> findByDistributorIdAndActiveTrue(UUID distributorId);
    Optional<TaxRate> findByDistributorIdAndCode(UUID distributorId, String code);
    boolean existsByDistributorIdAndCode(UUID distributorId, String code);
}
