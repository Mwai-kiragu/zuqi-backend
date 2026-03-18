package com.zuqi.repository;

import com.zuqi.domain.ai.ChurnPrediction;
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
 * Repository for churn predictions.
 * Supports at-risk customer identification and retention campaign targeting.
 */
@Repository
public interface ChurnPredictionRepository extends JpaRepository<ChurnPrediction, UUID> {

    /**
     * Find the churn prediction for a specific customer.
     */
    Optional<ChurnPrediction> findByDistributorIdAndCustomerId(UUID distributorId, UUID customerId);

    /**
     * Find all customers in a specific churn risk tier (e.g. HIGH, MEDIUM, LOW).
     */
    List<ChurnPrediction> findByDistributorIdAndRiskTier(UUID distributorId, String riskTier);

    /**
     * Paginated list of churn predictions for a distributor, highest probability first.
     */
    @Query("SELECT p FROM ChurnPrediction p WHERE p.distributor.id = :distributorId " +
            "ORDER BY p.churnProbability DESC")
    Page<ChurnPrediction> findByDistributorId(
            @Param("distributorId") UUID distributorId,
            Pageable pageable);

    /**
     * Find all customers whose churn probability meets or exceeds the given threshold.
     */
    @Query("SELECT p FROM ChurnPrediction p " +
            "WHERE p.distributor.id = :distributorId " +
            "AND p.churnProbability >= :threshold " +
            "ORDER BY p.churnProbability DESC")
    List<ChurnPrediction> findAtRiskCustomers(
            @Param("distributorId") UUID distributorId,
            @Param("threshold") Double threshold);
}
