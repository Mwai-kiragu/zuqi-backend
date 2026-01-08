package com.zuqi.repository;

import com.zuqi.domain.inventory.StockMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository for StockMovement entity operations.
 */
@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {

    /**
     * Find movements by product.
     */
    Page<StockMovement> findByProductIdOrderByCreatedAtDesc(UUID productId, Pageable pageable);

    /**
     * Find movements by warehouse.
     */
    Page<StockMovement> findByWarehouseIdOrderByCreatedAtDesc(UUID warehouseId, Pageable pageable);

    /**
     * Find movements by warehouse and product.
     */
    Page<StockMovement> findByWarehouseIdAndProductIdOrderByCreatedAtDesc(
            UUID warehouseId, UUID productId, Pageable pageable);

    /**
     * Find movements by reference.
     */
    List<StockMovement> findByReferenceTypeAndReferenceId(String referenceType, UUID referenceId);

    /**
     * Find movements within date range.
     */
    @Query("SELECT sm FROM StockMovement sm WHERE sm.warehouse.id = :warehouseId " +
            "AND sm.createdAt BETWEEN :startDate AND :endDate ORDER BY sm.createdAt DESC")
    Page<StockMovement> findByWarehouseIdAndDateRange(
            @Param("warehouseId") UUID warehouseId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);

    /**
     * Find movements by movement type.
     */
    Page<StockMovement> findByWarehouseIdAndMovementTypeOrderByCreatedAtDesc(
            UUID warehouseId, StockMovement.MovementType movementType, Pageable pageable);
}
