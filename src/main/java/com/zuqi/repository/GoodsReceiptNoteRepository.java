package com.zuqi.repository;

import com.zuqi.domain.procurement.GoodsReceiptNote;
import com.zuqi.domain.procurement.GrnStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GoodsReceiptNoteRepository extends JpaRepository<GoodsReceiptNote, UUID> {

    Optional<GoodsReceiptNote> findByGrnNumber(String grnNumber);

    List<GoodsReceiptNote> findByPurchaseOrderId(UUID purchaseOrderId);

    @Query("SELECT COUNT(g) FROM GoodsReceiptNote g")
    long countAll();

    @Query("SELECT g FROM GoodsReceiptNote g WHERE " +
           "(:distributorId IS NULL OR g.distributorId = :distributorId) AND " +
           "(:status IS NULL OR g.status = :status) AND " +
           "(:supplierId IS NULL OR g.supplier.id = :supplierId) AND " +
           "(:purchaseOrderId IS NULL OR g.purchaseOrder.id = :purchaseOrderId)")
    Page<GoodsReceiptNote> findWithFilters(
            @Param("distributorId") UUID distributorId,
            @Param("status") GrnStatus status,
            @Param("supplierId") UUID supplierId,
            @Param("purchaseOrderId") UUID purchaseOrderId,
            Pageable pageable);
}
