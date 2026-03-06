package com.zuqi.repository;

import com.zuqi.domain.inventory.StockTakeBatch;
import com.zuqi.domain.inventory.StockTakeBatchStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface StockTakeBatchRepository extends JpaRepository<StockTakeBatch, UUID> {

    Page<StockTakeBatch> findByWarehouseId(UUID warehouseId, Pageable pageable);

    Page<StockTakeBatch> findByBranchId(UUID branchId, Pageable pageable);

    Page<StockTakeBatch> findByWarehouseIdAndStatus(UUID warehouseId, StockTakeBatchStatus status, Pageable pageable);
}
