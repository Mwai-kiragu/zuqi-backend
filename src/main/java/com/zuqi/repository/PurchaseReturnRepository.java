package com.zuqi.repository;

import com.zuqi.domain.returns.PurchaseReturn;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public interface PurchaseReturnRepository extends JpaRepository<PurchaseReturn, UUID> {

    Page<PurchaseReturn> findByDistributorId(UUID distributorId, Pageable pageable);

    Page<PurchaseReturn> findByDistributorMerchantId(UUID merchantId, Pageable pageable);

    @Query("""
        SELECT pr FROM PurchaseReturn pr
        WHERE (:distributorId IS NULL OR pr.distributor.id = :distributorId)
          AND (:merchantId    IS NULL OR pr.distributor.merchant.id = :merchantId)
          AND (:status        IS NULL OR pr.status = :status)
          AND (:search        IS NULL OR :search = ''
               OR LOWER(pr.returnNumber) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(pr.supplier.name) LIKE LOWER(CONCAT('%', :search, '%'))
               OR (pr.grn IS NOT NULL AND LOWER(pr.grn.grnNumber) LIKE LOWER(CONCAT('%', :search, '%'))))
        ORDER BY pr.createdAt DESC
        """)
    Page<PurchaseReturn> findWithFilters(
            @Param("distributorId") UUID distributorId,
            @Param("merchantId") UUID merchantId,
            @Param("status") com.zuqi.domain.returns.ReturnStatus status,
            @Param("search") String search,
            Pageable pageable);

    boolean existsByReturnNumber(String returnNumber);

    /** Sum of already-returned quantities per product for a given GRN, excluding cancelled returns. */
    @Query("""
        SELECT pri.product.id, SUM(pri.quantity)
        FROM PurchaseReturn pr
        JOIN pr.items pri
        WHERE pr.grn.id = :grnId
          AND pr.status <> com.zuqi.domain.returns.ReturnStatus.CANCELLED
        GROUP BY pri.product.id
        """)
    List<Object[]> sumReturnedQuantitiesByGrnId(@Param("grnId") UUID grnId);
}
