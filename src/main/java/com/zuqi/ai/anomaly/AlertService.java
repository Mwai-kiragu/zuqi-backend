package com.zuqi.ai.anomaly;

import com.zuqi.domain.ai.AlertSeverity;
import com.zuqi.domain.ai.AlertStatus;
import com.zuqi.domain.ai.AlertType;
import com.zuqi.domain.ai.AnomalyAlert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Map;
import java.util.UUID;

/**
 * Centralized alert management for all anomaly detectors.
 *
 * Handles creation, deduplication, status transitions, and querying
 * of anomaly alerts from shrinkage detection, payment anomalies, and data quality.
 *
 * Blueprint reference: plan.md Section 6.3 - AlertService
 * Implementation plan: Phase 4, Task 4.4
 */
public interface AlertService {

    /**
     * Create or update an anomaly alert.
     *
     * Deduplication: if an OPEN or ACKNOWLEDGED alert for the same
     * entity + alertType exists within the last 24 hours, the existing
     * alert is updated (anomaly score and context refreshed) instead of
     * creating a new one.
     *
     * @param alertType     Type of anomaly detected
     * @param severity      Alert severity
     * @param entityType    Type of entity (e.g. "warehouse_sku", "payment", "order")
     * @param entityId      ID of the entity that triggered the alert
     * @param distributorId Distributor scope for multi-tenancy
     * @param anomalyScore  ML model anomaly score (0-1, higher = more anomalous)
     * @param description   Human-readable description of the anomaly
     * @param context       Supporting data for investigation (JSONB)
     * @return Persisted alert (new or updated existing)
     */
    AnomalyAlert createAlert(
            AlertType alertType,
            AlertSeverity severity,
            String entityType,
            UUID entityId,
            UUID distributorId,
            Double anomalyScore,
            String description,
            Map<String, Object> context);

    /**
     * Acknowledge an alert — confirms it has been seen by an operator.
     */
    AnomalyAlert acknowledgeAlert(UUID alertId, String acknowledgedBy);

    /**
     * Resolve an alert — marks it as investigated and closed.
     */
    AnomalyAlert resolveAlert(UUID alertId, String resolvedBy, String resolutionNote);

    /**
     * Dismiss an alert — marks it as a false positive.
     */
    AnomalyAlert dismissAlert(UUID alertId, String dismissedBy);

    /**
     * Get a single alert by ID.
     * @throws IllegalArgumentException if not found
     */
    AnomalyAlert getAlert(UUID alertId);

    /**
     * Paginated alert listing for a distributor with optional filters.
     */
    Page<AnomalyAlert> getAlerts(UUID distributorId, AlertStatus status,
                                  AlertType alertType, AlertSeverity severity,
                                  Pageable pageable);

    /**
     * Alert summary counts for the dashboard widget.
     * Returns counts grouped by severity for OPEN alerts.
     */
    AlertSummary getAlertSummary(UUID distributorId);

    record AlertSummary(
            long totalOpen,
            long critical,
            long high,
            long medium,
            long low
    ) {}
}
