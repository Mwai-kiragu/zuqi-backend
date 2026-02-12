package com.zuqi.repository;

import com.zuqi.domain.ai.AIPrediction;
import com.zuqi.domain.ai.EntityType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AIPredictionRepository extends JpaRepository<AIPrediction, UUID> {

    @Query("SELECT p FROM AIPrediction p WHERE p.entityType = :entityType " +
            "AND p.entityId = :entityId ORDER BY p.createdAt DESC")
    Page<AIPrediction> findByEntity(
            @Param("entityType") EntityType entityType,
            @Param("entityId") UUID entityId,
            Pageable pageable);

    @Query("SELECT p FROM AIPrediction p WHERE p.entityType = :entityType " +
            "AND p.entityId = :entityId ORDER BY p.createdAt DESC")
    Optional<AIPrediction> findLatestByEntity(
            @Param("entityType") EntityType entityType,
            @Param("entityId") UUID entityId);

    @Query("SELECT p FROM AIPrediction p WHERE p.modelName = :modelName " +
            "AND p.modelVersion = :modelVersion")
    List<AIPrediction> findByModelNameAndVersion(
            @Param("modelName") String modelName,
            @Param("modelVersion") Integer modelVersion);

    @Query("SELECT p FROM AIPrediction p WHERE p.distributor.id = :distributorId " +
            "AND p.createdAt >= :since")
    List<AIPrediction> findRecentByDistributor(
            @Param("distributorId") UUID distributorId,
            @Param("since") LocalDateTime since);

    @Query("SELECT p FROM AIPrediction p WHERE p.wasOverridden = true " +
            "AND p.createdAt >= :since")
    List<AIPrediction> findOverriddenSince(@Param("since") LocalDateTime since);

    Long countByModelNameAndModelVersion(String modelName, Integer modelVersion);
}
