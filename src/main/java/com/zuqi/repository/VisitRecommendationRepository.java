package com.zuqi.repository;

import com.zuqi.domain.ai.VisitRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for visit recommendations.
 * Supports sales rep scheduling and customer visit prioritisation.
 */
@Repository
public interface VisitRecommendationRepository extends JpaRepository<VisitRecommendation, UUID> {

    /**
     * Find all visit recommendations for a sales rep, highest conversion first.
     */
    List<VisitRecommendation> findBySalesRepIdOrderByPredictedConversionDesc(UUID salesRepId);

    /**
     * Find the visit recommendation for a specific customer (one per distributor-rep-customer triple).
     */
    Optional<VisitRecommendation> findByDistributorIdAndCustomerId(UUID distributorId, UUID customerId);

    /**
     * Find all visit recommendations for a sales rep within a distributor.
     */
    List<VisitRecommendation> findBySalesRepIdAndDistributorId(UUID salesRepId, UUID distributorId);
}
