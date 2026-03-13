package com.zuqi.repository;

import com.zuqi.domain.procurement.PoStatus;
import com.zuqi.domain.procurement.PurchaseOrder;
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
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {

    Page<PurchaseOrder> findByDistributorId(UUID distributorId, Pageable pageable);

    Page<PurchaseOrder> findBySupplierId(UUID supplierId, Pageable pageable);

    Optional<PurchaseOrder> findByPoNumber(String poNumber);

    @Query("SELECT COUNT(po) FROM PurchaseOrder po")
    long countAll();

    @Query("SELECT po FROM PurchaseOrder po WHERE " +
            "(:distributorId IS NULL OR po.distributorId = :distributorId) AND " +
            "(:status IS NULL OR po.status = :status) AND " +
            "(:supplierId IS NULL OR po.supplier.id = :supplierId)")
    Page<PurchaseOrder> findWithFilters(@Param("distributorId") UUID distributorId,
                                        @Param("status") PoStatus status,
                                        @Param("supplierId") UUID supplierId,
                                        Pageable pageable);

    @Query("SELECT po FROM PurchaseOrder po JOIN FETCH po.supplier WHERE po.distributorId = :distributorId " +
           "AND po.status IN ('CONFIRMED', 'PARTIALLY_RECEIVED', 'RECEIVED')")
    List<PurchaseOrder> findOutstandingByDistributorId(@Param("distributorId") UUID distributorId);
}
