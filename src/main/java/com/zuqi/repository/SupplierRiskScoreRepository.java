package com.zuqi.repository;

import com.zuqi.domain.ai.SupplierRiskScore;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for supplier risk scores.
 * Supports procurement risk management and supplier performance monitoring.
 */
@Repository
public interface SupplierRiskScoreRepository extends JpaRepository<SupplierRiskScore, UUID> {

    /**
     * Find the risk score for a specific supplier.
     */
    Optional<SupplierRiskScore> findByDistributorIdAndSupplierId(UUID distributorId, UUID supplierId);

    /**
     * Find all suppliers in a specific risk tier (e.g. HIGH, MEDIUM, LOW).
     */
    List<SupplierRiskScore> findByDistributorIdAndRiskTier(UUID distributorId, String riskTier);

    /**
     * Paginated list of supplier risk scores for a distributor,
     * lowest score (highest risk) first.
     */
    @Query("SELECT s FROM SupplierRiskScore s WHERE s.distributor.id = :distributorId " +
            "ORDER BY s.riskScore ASC")
    Page<SupplierRiskScore> findByDistributorId(
            @Param("distributorId") UUID distributorId,
            Pageable pageable);
}
