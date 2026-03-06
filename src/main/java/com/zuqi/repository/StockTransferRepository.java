package com.zuqi.repository;

import com.zuqi.domain.inventory.StockTransfer;
import com.zuqi.domain.inventory.StockTransferStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StockTransferRepository extends JpaRepository<StockTransfer, UUID> {

    Page<StockTransfer> findBySourceWarehouseIdOrDestinationWarehouseId(UUID sourceId, UUID destId, Pageable pageable);

    Page<StockTransfer> findByStatus(StockTransferStatus status, Pageable pageable);

    List<StockTransfer> findBySourceBranchIdOrDestinationBranchId(UUID sourceId, UUID destId);

    Page<StockTransfer> findBySourceBranchIdOrDestinationBranchId(UUID sourceId, UUID destId, Pageable pageable);

    Page<StockTransfer> findBySourceBranchIdAndStatus(UUID sourceBranchId, StockTransferStatus status, Pageable pageable);

    Page<StockTransfer> findByDestinationBranchIdAndStatus(UUID destBranchId, StockTransferStatus status, Pageable pageable);
}
