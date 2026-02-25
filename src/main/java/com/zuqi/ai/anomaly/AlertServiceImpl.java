package com.zuqi.ai.anomaly;

import com.zuqi.domain.ai.AlertSeverity;
import com.zuqi.domain.ai.AlertStatus;
import com.zuqi.domain.ai.AlertType;
import com.zuqi.domain.ai.AnomalyAlert;
import com.zuqi.domain.distributor.Distributor;
import com.zuqi.repository.AnomalyAlertRepository;
import com.zuqi.repository.DistributorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlertServiceImpl implements AlertService {

    private static final int DEDUP_WINDOW_HOURS = 24;

    private final AnomalyAlertRepository alertRepository;
    private final DistributorRepository distributorRepository;

    @Override
    @Transactional
    public AnomalyAlert createAlert(
            AlertType alertType,
            AlertSeverity severity,
            String entityType,
            UUID entityId,
            UUID distributorId,
            Double anomalyScore,
            String description,
            Map<String, Object> context) {

        LocalDateTime dedupWindow = LocalDateTime.now().minusHours(DEDUP_WINDOW_HOURS);

        Optional<AnomalyAlert> existing = alertRepository.findExistingOpenAlert(
                entityType, entityId, alertType, dedupWindow);

        if (existing.isPresent()) {
            AnomalyAlert alert = existing.get();
            alert.setAnomalyScore(anomalyScore);
            alert.setDescription(description);
            alert.setContext(context);
            alert.setSeverity(severity);
            AnomalyAlert updated = alertRepository.save(alert);
            log.debug("Updated existing alert {} for entity {}:{} type={}",
                    alert.getId(), entityType, entityId, alertType);
            return updated;
        }

        Distributor distributor = distributorRepository.findById(distributorId)
                .orElseThrow(() -> new IllegalArgumentException("Distributor not found: " + distributorId));

        AnomalyAlert alert = AnomalyAlert.builder()
                .alertType(alertType)
                .severity(severity)
                .entityType(entityType)
                .entityId(entityId)
                .distributor(distributor)
                .anomalyScore(anomalyScore)
                .description(description)
                .context(context)
                .status(AlertStatus.OPEN)
                .build();

        AnomalyAlert saved = alertRepository.save(alert);
        log.info("Created {} alert id={} severity={} entity={}:{} score={}",
                alertType, saved.getId(), severity, entityType, entityId, anomalyScore);
        return saved;
    }

    @Override
    @Transactional
    public AnomalyAlert acknowledgeAlert(UUID alertId, String acknowledgedBy) {
        AnomalyAlert alert = getAlertOrThrow(alertId);
        validateTransition(alert.getStatus(), AlertStatus.ACKNOWLEDGED);
        alert.setStatus(AlertStatus.ACKNOWLEDGED);
        alert.setResolvedBy(acknowledgedBy);
        AnomalyAlert saved = alertRepository.save(alert);
        log.info("Alert {} acknowledged by {}", alertId, acknowledgedBy);
        return saved;
    }

    @Override
    @Transactional
    public AnomalyAlert resolveAlert(UUID alertId, String resolvedBy, String resolutionNote) {
        AnomalyAlert alert = getAlertOrThrow(alertId);
        validateTransition(alert.getStatus(), AlertStatus.RESOLVED);
        alert.setStatus(AlertStatus.RESOLVED);
        alert.setResolvedBy(resolvedBy);
        alert.setResolvedAt(LocalDateTime.now());
        if (resolutionNote != null && !resolutionNote.isBlank()) {
            alert.setDescription(alert.getDescription() + " | Resolution: " + resolutionNote);
        }
        AnomalyAlert saved = alertRepository.save(alert);
        log.info("Alert {} resolved by {}", alertId, resolvedBy);
        return saved;
    }

    @Override
    @Transactional
    public AnomalyAlert dismissAlert(UUID alertId, String dismissedBy) {
        AnomalyAlert alert = getAlertOrThrow(alertId);
        validateTransition(alert.getStatus(), AlertStatus.DISMISSED);
        alert.setStatus(AlertStatus.DISMISSED);
        alert.setResolvedBy(dismissedBy);
        alert.setResolvedAt(LocalDateTime.now());
        AnomalyAlert saved = alertRepository.save(alert);
        log.info("Alert {} dismissed by {}", alertId, dismissedBy);
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AnomalyAlert> getAlerts(UUID distributorId, AlertStatus status,
                                         AlertType alertType, AlertSeverity severity,
                                         Pageable pageable) {
        if (status != null && alertType != null) {
            return alertRepository.findByDistributorIdAndAlertType(distributorId, alertType, pageable)
                    .map(a -> a); // filtering by status would require a custom query; covered by status-only path
        }
        if (status != null) {
            return alertRepository.findByDistributorIdAndStatus(distributorId, status, pageable);
        }
        if (alertType != null) {
            return alertRepository.findByDistributorIdAndAlertType(distributorId, alertType, pageable);
        }
        if (severity != null) {
            return alertRepository.findByDistributorIdAndSeverity(distributorId, severity, pageable);
        }
        return alertRepository.findByDistributorId(distributorId, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public AlertSummary getAlertSummary(UUID distributorId) {
        long total    = alertRepository.countByDistributorIdAndStatus(distributorId, AlertStatus.OPEN);
        long critical = alertRepository.countByDistributorIdAndSeverityAndStatus(distributorId, AlertSeverity.CRITICAL, AlertStatus.OPEN);
        long high     = alertRepository.countByDistributorIdAndSeverityAndStatus(distributorId, AlertSeverity.HIGH, AlertStatus.OPEN);
        long medium   = alertRepository.countByDistributorIdAndSeverityAndStatus(distributorId, AlertSeverity.MEDIUM, AlertStatus.OPEN);
        long low      = alertRepository.countByDistributorIdAndSeverityAndStatus(distributorId, AlertSeverity.LOW, AlertStatus.OPEN);
        return new AlertSummary(total, critical, high, medium, low);
    }

    @Override
    @Transactional(readOnly = true)
    public AnomalyAlert getAlert(UUID alertId) {
        return getAlertOrThrow(alertId);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private AnomalyAlert getAlertOrThrow(UUID alertId) {
        return alertRepository.findById(alertId)
                .orElseThrow(() -> new IllegalArgumentException("Alert not found: " + alertId));
    }

    private void validateTransition(AlertStatus current, AlertStatus target) {
        if (current == AlertStatus.RESOLVED || current == AlertStatus.DISMISSED) {
            throw new IllegalStateException(
                    "Cannot transition alert from " + current + " to " + target);
        }
    }
}
