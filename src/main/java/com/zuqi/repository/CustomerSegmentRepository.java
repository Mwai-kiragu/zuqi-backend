package com.zuqi.repository;

import com.zuqi.domain.ai.CustomerSegment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for customer segments.
 * Supports per-customer lookup and distributor-wide segment analytics.
 */
@Repository
public interface CustomerSegmentRepository extends JpaRepository<CustomerSegment, UUID> {

    /**
     * Find the current segment assignment for a specific customer.
     */
    Optional<CustomerSegment> findByDistributorIdAndCustomerId(UUID distributorId, UUID customerId);

    /**
     * Paginated list of all segments for a distributor.
     */
    Page<CustomerSegment> findByDistributorId(UUID distributorId, Pageable pageable);

    /**
     * Find all customers assigned to a specific segment label.
     */
    List<CustomerSegment> findByDistributorIdAndSegmentLabel(UUID distributorId, String segmentLabel);

    /**
     * Count customers in a specific segment label for a distributor.
     */
    long countByDistributorIdAndSegmentLabel(UUID distributorId, String segmentLabel);
}
