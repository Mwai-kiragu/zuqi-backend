package com.zuqi.repository;

import com.zuqi.domain.ai.PriceTrend;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for price trends.
 * Supports procurement timing decisions based on supplier-product price direction.
 */
@Repository
public interface PriceTrendRepository extends JpaRepository<PriceTrend, UUID> {

    /**
     * Find the price trend for a specific supplier-product combination.
     */
    Optional<PriceTrend> findByDistributorIdAndSupplierIdAndProductId(
            UUID distributorId,
            UUID supplierId,
            UUID productId);

    /**
     * Find all price trends matching a specific direction (e.g. RISING, FALLING, STABLE).
     */
    List<PriceTrend> findByDistributorIdAndTrendDirection(UUID distributorId, String trendDirection);

    /**
     * Find all price trends for a distributor.
     */
    List<PriceTrend> findByDistributorId(UUID distributorId);
}
