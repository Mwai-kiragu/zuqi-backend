package com.zuqi.repository;

import com.zuqi.domain.ai.CustomerHealthScore;
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
 * Repository for customer health scores.
 * Supports tier-based customer prioritisation for sales operations.
 */
@Repository
public interface CustomerHealthScoreRepository extends JpaRepository<CustomerHealthScore, UUID> {

    /**
     * Find the health score record for a specific customer.
     */
    Optional<CustomerHealthScore> findByDistributorIdAndCustomerId(UUID distributorId, UUID customerId);

    /**
     * Find all customers in a given health tier (e.g. HEALTHY, AT_RISK, CRITICAL).
     */
    List<CustomerHealthScore> findByDistributorIdAndHealthTier(UUID distributorId, String healthTier);

    /**
     * Paginated list of all health scores for a distributor, highest score first.
     */
    @Query("SELECT h FROM CustomerHealthScore h WHERE h.distributor.id = :distributorId " +
            "ORDER BY h.healthScore DESC")
    Page<CustomerHealthScore> findByDistributorId(
            @Param("distributorId") UUID distributorId,
            Pageable pageable);
}
