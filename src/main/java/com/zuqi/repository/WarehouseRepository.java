package com.zuqi.repository;

import com.zuqi.domain.inventory.Warehouse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Warehouse entity operations.
 */
@Repository
public interface WarehouseRepository extends JpaRepository<Warehouse, UUID> {

    /**
     * Find all active warehouses.
     */
    List<Warehouse> findByActiveTrue();

    /**
     * Find warehouses by distributor.
     */
    List<Warehouse> findByDistributorIdAndActiveTrue(UUID distributorId);

    /**
     * Find warehouses by distributor with pagination.
     */
    Page<Warehouse> findByDistributorIdAndActiveTrue(UUID distributorId, Pageable pageable);

    /**
     * Find warehouse by code and distributor.
     */
    Optional<Warehouse> findByCodeAndDistributorId(String code, UUID distributorId);

    /**
     * Check if warehouse exists by code and distributor.
     */
    boolean existsByCodeAndDistributorId(String code, UUID distributorId);

    /**
     * Find warehouses by manager.
     */
    List<Warehouse> findByManagerIdAndActiveTrue(UUID managerId);
}
