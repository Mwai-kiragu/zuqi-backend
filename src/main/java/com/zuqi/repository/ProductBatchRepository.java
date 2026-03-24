package com.zuqi.repository;

import com.zuqi.domain.inventory.ProductBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Repository for product batches.
 * Supports FEFO (First Expired, First Out) inventory management queries.
 */
@Repository
public interface ProductBatchRepository extends JpaRepository<ProductBatch, UUID> {

    /**
     * Find all batches for a specific product in a given warehouse.
     */
    List<ProductBatch> findByWarehouseIdAndProductId(UUID warehouseId, UUID productId);

    /**
     * Find all batches for a distributor filtered by status.
     */
    List<ProductBatch> findByDistributorIdAndStatus(UUID distributorId, String status);

    /**
     * Find all active batches that expire on or before the given date, with
     * remaining stock, ordered by nearest expiry date first.
     */
    @Query("SELECT b FROM ProductBatch b " +
            "WHERE b.distributor.id = :distributorId " +
            "AND b.expiryDate BETWEEN CURRENT_DATE AND :endDate " +
            "AND b.currentQuantity > 0 " +
            "ORDER BY b.expiryDate ASC")
    List<ProductBatch> findExpiringBatches(
            @Param("distributorId") UUID distributorId,
            @Param("endDate") LocalDate endDate);

    /**
     * Count batches in ACTIVE status for a given distributor.
     */
    @Query("SELECT COUNT(b) FROM ProductBatch b " +
            "WHERE b.distributor.id = :distributorId AND b.status = 'ACTIVE'")
    long countActiveByDistributorId(@Param("distributorId") UUID distributorId);
}
