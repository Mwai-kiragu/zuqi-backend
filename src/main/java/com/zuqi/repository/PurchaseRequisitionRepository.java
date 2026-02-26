package com.zuqi.repository;

import com.zuqi.domain.procurement.PrStatus;
import com.zuqi.domain.procurement.PurchaseRequisition;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PurchaseRequisitionRepository extends JpaRepository<PurchaseRequisition, UUID> {

    Page<PurchaseRequisition> findByDistributorId(UUID distributorId, Pageable pageable);

    Page<PurchaseRequisition> findByDistributorIdAndStatus(UUID distributorId, PrStatus status, Pageable pageable);

    Page<PurchaseRequisition> findByRequestedById(UUID userId, Pageable pageable);

    Optional<PurchaseRequisition> findByPrNumber(String prNumber);

    @Query("SELECT COUNT(pr) FROM PurchaseRequisition pr")
    long countAll();

    @Query("SELECT pr FROM PurchaseRequisition pr WHERE " +
            "(:distributorId IS NULL OR pr.distributorId = :distributorId) AND " +
            "(:status IS NULL OR pr.status = :status) AND " +
            "(:requestedById IS NULL OR pr.requestedBy.id = :requestedById)")
    Page<PurchaseRequisition> findWithFilters(@Param("distributorId") UUID distributorId,
                                              @Param("status") PrStatus status,
                                              @Param("requestedById") UUID requestedById,
                                              Pageable pageable);
}
