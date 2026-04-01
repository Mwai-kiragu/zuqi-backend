package com.zuqi.repository;

import com.zuqi.domain.pricing.Promotion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PromotionRepository extends JpaRepository<Promotion, UUID> {

    Page<Promotion> findByDistributorId(UUID distributorId, Pageable pageable);

    @Query("SELECT p FROM Promotion p WHERE p.distributor.merchant.id = :merchantId")
    Page<Promotion> findByDistributorMerchantId(@Param("merchantId") UUID merchantId, Pageable pageable);

    List<Promotion> findByDistributorIdAndActiveTrueAndValidFromLessThanEqualAndValidToGreaterThanEqual(
            UUID distributorId, LocalDate validFrom, LocalDate validTo);

    /**
     * Returns active, approved promotions that apply to the given product (product-specific first,
     * then general/no-product promotions). Caller should take the first result.
     */
    @Query("""
            SELECT p FROM Promotion p
            WHERE p.distributor.id = :distributorId
              AND p.active = true
              AND p.approvalStatus = 'APPROVED'
              AND p.validFrom <= :today
              AND p.validTo >= :today
              AND (p.product IS NULL OR p.product.id = :productId)
            ORDER BY p.product.id NULLS LAST
            """)
    List<Promotion> findActiveApprovedPromotionsForProductOrGeneral(
            @Param("distributorId") UUID distributorId,
            @Param("productId") UUID productId,
            @Param("today") LocalDate today);

    @Modifying
    @Query("UPDATE Promotion pr SET pr.approvalStatus = :status WHERE pr.id = :id")
    void updateApprovalStatus(@Param("id") UUID id, @Param("status") String status);
}
