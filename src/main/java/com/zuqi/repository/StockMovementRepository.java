package com.zuqi.repository;

import com.zuqi.domain.inventory.StockMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {

    Page<StockMovement> findByProductIdOrderByCreatedAtDesc(UUID productId, Pageable pageable);

    Page<StockMovement> findByWarehouseIdOrderByCreatedAtDesc(UUID warehouseId, Pageable pageable);

    Page<StockMovement> findByWarehouseIdAndProductIdOrderByCreatedAtDesc(
            UUID warehouseId, UUID productId, Pageable pageable);

    List<StockMovement> findByReferenceTypeAndReferenceId(String referenceType, UUID referenceId);

    @Query("SELECT sm FROM StockMovement sm WHERE sm.warehouse.id = :warehouseId " +
            "AND sm.createdAt BETWEEN :startDate AND :endDate ORDER BY sm.createdAt DESC")
    Page<StockMovement> findByWarehouseIdAndDateRange(
            @Param("warehouseId") UUID warehouseId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);

    Page<StockMovement> findByWarehouseIdAndMovementTypeOrderByCreatedAtDesc(
            UUID warehouseId, StockMovement.MovementType movementType, Pageable pageable);

    @Modifying
    @Query("UPDATE StockMovement sm SET sm.approvalStatus = :status WHERE sm.id = :id")
    void updateApprovalStatus(@Param("id") UUID id, @Param("status") String status);
}
