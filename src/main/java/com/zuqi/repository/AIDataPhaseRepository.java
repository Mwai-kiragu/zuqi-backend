package com.zuqi.repository;

import com.zuqi.domain.ai.AIDataPhase;
import com.zuqi.domain.ai.DataPhase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AIDataPhaseRepository extends JpaRepository<AIDataPhase, UUID> {

    /** Find the phase record for a specific model + distributor (null distributor = global). */
    @Query("SELECT p FROM AIDataPhase p WHERE p.modelName = :modelName " +
           "AND (:distributorId IS NULL AND p.distributor IS NULL " +
           "     OR p.distributor.id = :distributorId)")
    Optional<AIDataPhase> findByModelNameAndDistributorId(
            @Param("modelName") String modelName,
            @Param("distributorId") UUID distributorId);

    /** All phase records for a model across all distributors. */
    List<AIDataPhase> findByModelName(String modelName);

    /** All models currently in a given phase (e.g. find all SYNTHETIC models). */
    List<AIDataPhase> findByCurrentPhase(DataPhase currentPhase);

    /** All phase records for a distributor. */
    @Query("SELECT p FROM AIDataPhase p WHERE p.distributor.id = :distributorId")
    List<AIDataPhase> findByDistributorId(@Param("distributorId") UUID distributorId);

    /** Models that have transitioned at least once (transitionedAt is set). */
    @Query("SELECT p FROM AIDataPhase p WHERE p.transitionedAt IS NOT NULL " +
           "ORDER BY p.transitionedAt DESC")
    List<AIDataPhase> findTransitionedModels();
}
