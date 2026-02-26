package com.zuqi.repository;

import com.zuqi.domain.ai.AISyntheticRun;
import com.zuqi.domain.ai.SyntheticRunStatus;
import com.zuqi.domain.ai.SyntheticRunType;
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
public interface AISyntheticRunRepository extends JpaRepository<AISyntheticRun, UUID> {

    /** Most recent completed run for a distributor (for reproducibility lookups). */
    @Query("SELECT r FROM AISyntheticRun r WHERE r.distributor.id = :distributorId " +
           "AND r.status = 'COMPLETED' ORDER BY r.completedAt DESC")
    List<AISyntheticRun> findLatestCompletedByDistributor(
            @Param("distributorId") UUID distributorId,
            Pageable pageable);

    default Optional<AISyntheticRun> findLatestCompletedByDistributor(UUID distributorId) {
        return findLatestCompletedByDistributor(distributorId, Pageable.ofSize(1))
                .stream().findFirst();
    }

    /** All runs for a distributor, most recent first. */
    @Query("SELECT r FROM AISyntheticRun r WHERE r.distributor.id = :distributorId " +
           "ORDER BY r.startedAt DESC")
    List<AISyntheticRun> findByDistributorIdOrderByStartedAtDesc(
            @Param("distributorId") UUID distributorId);

    /** Runs by status (e.g. find any stuck RUNNING runs). */
    List<AISyntheticRun> findByStatus(SyntheticRunStatus status);

    /** Runs by type (e.g. all FULL_SEED runs for audit). */
    List<AISyntheticRun> findByRunType(SyntheticRunType runType);

    /** Runs started before a cutoff (for cleanup/archiving). */
    @Query("SELECT r FROM AISyntheticRun r WHERE r.startedAt < :cutoff")
    List<AISyntheticRun> findRunsStartedBefore(@Param("cutoff") LocalDateTime cutoff);

    /** Count completed runs for a distributor (data maturity indicator). */
    @Query("SELECT COUNT(r) FROM AISyntheticRun r WHERE r.distributor.id = :distributorId " +
           "AND r.status = 'COMPLETED'")
    long countCompletedByDistributor(@Param("distributorId") UUID distributorId);
}
