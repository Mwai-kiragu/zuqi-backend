package com.zuqi.repository;

import com.zuqi.domain.ai.CustomerClv;
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
 * Repository for customer lifetime value predictions.
 * Supports high-value customer identification and strategic account planning.
 */
@Repository
public interface CustomerClvRepository extends JpaRepository<CustomerClv, UUID> {

    /**
     * Find the CLV record for a specific customer.
     */
    Optional<CustomerClv> findByDistributorIdAndCustomerId(UUID distributorId, UUID customerId);

    /**
     * Paginated list of CLV records for a distributor, highest predicted revenue first.
     */
    @Query("SELECT c FROM CustomerClv c WHERE c.distributor.id = :distributorId " +
            "ORDER BY c.predictedRevenue12m DESC")
    Page<CustomerClv> findByDistributorId(
            @Param("distributorId") UUID distributorId,
            Pageable pageable);

    /**
     * Top 10 customers by predicted 12-month revenue for a distributor.
     */
    @Query("SELECT c FROM CustomerClv c WHERE c.distributor.id = :distributorId " +
            "ORDER BY c.predictedRevenue12m DESC LIMIT 10")
    List<CustomerClv> findTopByDistributorId(@Param("distributorId") UUID distributorId);
}
