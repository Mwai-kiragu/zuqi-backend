package com.zuqi.repository;

import com.zuqi.domain.ai.AlertSeverity;
import com.zuqi.domain.ai.AlertStatus;
import com.zuqi.domain.ai.AlertType;
import com.zuqi.domain.ai.AnomalyAlert;
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
public interface AnomalyAlertRepository extends JpaRepository<AnomalyAlert, UUID> {

    Page<AnomalyAlert> findByDistributorId(UUID distributorId, Pageable pageable);

    Page<AnomalyAlert> findByDistributorIdAndStatus(UUID distributorId, AlertStatus status, Pageable pageable);

    Page<AnomalyAlert> findByDistributorIdAndAlertType(UUID distributorId, AlertType alertType, Pageable pageable);

    Page<AnomalyAlert> findByDistributorIdAndSeverity(UUID distributorId, AlertSeverity severity, Pageable pageable);

    List<AnomalyAlert> findByDistributorIdAndStatus(UUID distributorId, AlertStatus status);

    /**
     * Find open alert for same entity + type within a given time window.
     * Used for deduplication — avoids creating duplicate alerts.
     */
    @Query("SELECT a FROM AnomalyAlert a WHERE a.entityType = :entityType " +
           "AND a.entityId = :entityId AND a.alertType = :alertType " +
           "AND a.status IN ('OPEN', 'ACKNOWLEDGED') AND a.createdAt >= :since")
    Optional<AnomalyAlert> findExistingOpenAlert(
            @Param("entityType") String entityType,
            @Param("entityId") UUID entityId,
            @Param("alertType") AlertType alertType,
            @Param("since") LocalDateTime since);

    long countByDistributorIdAndStatus(UUID distributorId, AlertStatus status);

    long countByDistributorIdAndSeverityAndStatus(UUID distributorId, AlertSeverity severity, AlertStatus status);

    @Query("SELECT a.alertType, COUNT(a) FROM AnomalyAlert a " +
           "WHERE a.distributor.id = :distributorId AND a.status = 'OPEN' " +
           "GROUP BY a.alertType")
    List<Object[]> countOpenAlertsByType(@Param("distributorId") UUID distributorId);
}
