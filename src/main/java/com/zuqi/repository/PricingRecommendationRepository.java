package com.zuqi.repository;

import com.zuqi.domain.ai.PricingRecommendation;
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
 * Repository for pricing recommendations.
 * Supports sell-price optimisation and revenue impact analysis.
 */
@Repository
public interface PricingRecommendationRepository extends JpaRepository<PricingRecommendation, UUID> {

    /**
     * Find all pricing recommendations for a product within a distributor, most recent first.
     */
    List<PricingRecommendation> findByDistributorIdAndProductIdOrderByCreatedAtDesc(
            UUID distributorId,
            UUID productId);

    /**
     * Find all recommendations for a distributor filtered by workflow status.
     */
    List<PricingRecommendation> findByDistributorIdAndStatus(UUID distributorId, String status);

    /**
     * Paginated list of pricing recommendations for a distributor,
     * highest estimated revenue impact first.
     */
    @Query("SELECT r FROM PricingRecommendation r WHERE r.distributor.id = :distributorId " +
            "ORDER BY r.estimatedRevenueImpactKes DESC")
    Page<PricingRecommendation> findByDistributorId(
            @Param("distributorId") UUID distributorId,
            Pageable pageable);

    /**
     * Find the most recent pricing recommendation for a specific product.
     */
    @Query("SELECT r FROM PricingRecommendation r " +
            "WHERE r.distributor.id = :distributorId AND r.product.id = :productId " +
            "ORDER BY r.createdAt DESC LIMIT 1")
    Optional<PricingRecommendation> findLatestByDistributorIdAndProductId(
            @Param("distributorId") UUID distributorId,
            @Param("productId") UUID productId);
}
