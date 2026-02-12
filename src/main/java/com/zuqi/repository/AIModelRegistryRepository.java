package com.zuqi.repository;

import com.zuqi.domain.ai.AIModelRegistry;
import com.zuqi.domain.ai.ModelStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AIModelRegistryRepository extends JpaRepository<AIModelRegistry, UUID> {

    Optional<AIModelRegistry> findByModelNameAndModelVersion(String modelName, Integer modelVersion);

    @Query("SELECT m FROM AIModelRegistry m WHERE m.modelName = :modelName " +
            "AND m.status = :status ORDER BY m.modelVersion DESC")
    Optional<AIModelRegistry> findLatestActiveModel(
            @Param("modelName") String modelName,
            @Param("status") ModelStatus status);

    @Query("SELECT m FROM AIModelRegistry m WHERE m.modelName = :modelName " +
            "ORDER BY m.modelVersion DESC")
    List<AIModelRegistry> findAllVersionsByModelName(@Param("modelName") String modelName);

    List<AIModelRegistry> findByStatus(ModelStatus status);

    @Query("SELECT m FROM AIModelRegistry m WHERE m.distributor.id = :distributorId " +
            "AND m.status = :status")
    List<AIModelRegistry> findByDistributorAndStatus(
            @Param("distributorId") UUID distributorId,
            @Param("status") ModelStatus status);
}
