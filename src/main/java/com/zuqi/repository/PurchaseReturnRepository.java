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
