package com.zuqi.repository;

import com.zuqi.domain.ai.Recommendation;
import com.zuqi.domain.ai.RecommendationStatus;
import com.zuqi.domain.ai.RecommendationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RecommendationRepository extends JpaRepository<Recommendation, UUID> {

    Page<Recommendation> findByDistributorId(UUID distributorId, Pageable pageable);

    Page<Recommendation> findByDistributorIdAndStatus(UUID distributorId, RecommendationStatus status, Pageable pageable);

    List<Recommendation> findByDistributorIdAndStatusOrderByPriorityDescCreatedAtDesc(
            UUID distributorId, RecommendationStatus status);

    Page<Recommendation> findByDistributorIdAndRecommendationType(
            UUID distributorId, RecommendationType type, Pageable pageable);

    Page<Recommendation> findByDistributorIdAndStatusAndRecommendationType(
            UUID distributorId, RecommendationStatus status, RecommendationType type, Pageable pageable);

    long countByDistributorIdAndStatus(UUID distributorId, RecommendationStatus status);

    @Query("SELECT r FROM Recommendation r WHERE r.distributor.id = :distributorId " +
           "AND r.status = 'PENDING' ORDER BY r.priority DESC, r.createdAt DESC")
    List<Recommendation> findPendingByDistributor(@Param("distributorId") UUID distributorId);

    @Query("SELECT r FROM Recommendation r WHERE r.distributor.id = :distributorId " +
           "ORDER BY r.createdAt DESC")
    Page<Recommendation> findLatestByDistributor(@Param("distributorId") UUID distributorId, Pageable pageable);
}
