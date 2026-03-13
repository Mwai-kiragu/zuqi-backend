package com.zuqi.repository;

import com.zuqi.domain.inventory.StockTakeItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StockTakeItemRepository extends JpaRepository<StockTakeItem, UUID> {

    List<StockTakeItem> findByBatchId(UUID batchId);

    Optional<StockTakeItem> findByBatchIdAndProductId(UUID batchId, UUID productId);
}
