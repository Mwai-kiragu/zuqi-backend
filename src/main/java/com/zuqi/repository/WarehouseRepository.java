package com.zuqi.repository;

import com.zuqi.domain.inventory.Warehouse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WarehouseRepository extends JpaRepository<Warehouse, UUID> {

    List<Warehouse> findByActiveTrue();

    Page<Warehouse> findByActiveTrue(Pageable pageable);

    List<Warehouse> findByDistributorIdAndActiveTrue(UUID distributorId);

    Page<Warehouse> findByDistributorIdAndActiveTrue(UUID distributorId, Pageable pageable);

    Page<Warehouse> findByActiveFalse(Pageable pageable);

    Page<Warehouse> findByDistributorIdAndActiveFalse(UUID distributorId, Pageable pageable);

    Optional<Warehouse> findByCodeAndDistributorId(String code, UUID distributorId);
    boolean existsByCodeAndDistributorId(String code, UUID distributorId);

    List<Warehouse> findByManagerIdAndActiveTrue(UUID managerId);

    List<Warehouse> findByBranchIdAndActiveTrue(UUID branchId);
}
