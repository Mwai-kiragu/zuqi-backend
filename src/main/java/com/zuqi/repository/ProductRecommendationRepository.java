package com.zuqi.repository;

import com.zuqi.domain.ai.ProductRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for product recommendations.
 * Supports next-best-product suggestions and batch replacement before regeneration.
 */
@Repository
public interface ProductRecommendationRepository extends JpaRepository<ProductRecommendation, UUID> {

    /**
     * Find all product recommendations for a customer, best scored first.
     */
    List<ProductRecommendation> findByCustomerIdOrderByRecommendationScoreDesc(UUID customerId);

    /**
     * Find all recommendations for a specific customer within a distributor.
     */
    List<ProductRecommendation> findByDistributorIdAndCustomerId(UUID distributorId, UUID customerId);

    /**
     * Find all recommendations for a distributor, best scored first.
     */
    List<ProductRecommendation> findByDistributorIdOrderByRecommendationScoreDesc(UUID distributorId);

    /**
     * Delete all existing recommendations for a customer before regenerating a fresh batch.
     */
    @Modifying
    @Query("DELETE FROM ProductRecommendation r " +
            "WHERE r.distributor.id = :distributorId AND r.customer.id = :customerId")
    void deleteByDistributorIdAndCustomerId(
            @Param("distributorId") UUID distributorId,
            @Param("customerId") UUID customerId);
}
