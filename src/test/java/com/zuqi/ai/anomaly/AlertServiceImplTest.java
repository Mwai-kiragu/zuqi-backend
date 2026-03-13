package com.zuqi.ai.anomaly;

import com.zuqi.domain.ai.AlertSeverity;
import com.zuqi.domain.ai.AlertStatus;
import com.zuqi.domain.ai.AlertType;
import com.zuqi.domain.ai.AnomalyAlert;
import com.zuqi.domain.distributor.Distributor;
import com.zuqi.repository.AnomalyAlertRepository;
import com.zuqi.repository.DistributorRepository;
import com.zuqi.repository.UserRepository;
import com.zuqi.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AlertServiceImpl}.
 *
 * Covers: deduplication, status transitions, query delegation.
 */
@ExtendWith(MockitoExtension.class)
class AlertServiceImplTest {

    @Mock
    private AnomalyAlertRepository alertRepository;

    @Mock
    private DistributorRepository distributorRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private AlertServiceImpl alertService;

    private UUID distributorId;
    private UUID entityId;
    private Distributor distributor;

    @BeforeEach
    void setUp() {
        distributorId = UUID.randomUUID();
        entityId      = UUID.randomUUID();

        distributor = new Distributor();
        distributor.setId(distributorId);
    }

    // ── createAlert ───────────────────────────────────────────────────────

    @Test
    void createAlert_whenNoExistingAlert_savesNewAlert() {
        when(alertRepository.findExistingOpenAlert(any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        when(distributorRepository.findById(distributorId))
                .thenReturn(Optional.of(distributor));
        when(userRepository.findByDistributorIdAndActiveTrue(distributorId))
                .thenReturn(List.of());

        AnomalyAlert savedAlert = buildAlert(AlertStatus.OPEN);
        when(alertRepository.save(any(AnomalyAlert.class))).thenReturn(savedAlert);

        AnomalyAlert result = alertService.createAlert(
                AlertType.SHRINKAGE, AlertSeverity.HIGH,
                "STOCK", entityId, distributorId,
                0.85, "Shrinkage detected", Map.of());

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(AlertStatus.OPEN);
        verify(alertRepository).save(any(AnomalyAlert.class));
    }

    @Test
    void createAlert_whenDuplicateWithin24h_updatesExistingAlert() {
        AnomalyAlert existing = buildAlert(AlertStatus.OPEN);
        when(alertRepository.findExistingOpenAlert(any(), any(), any(), any()))
                .thenReturn(Optional.of(existing));
        when(alertRepository.save(existing)).thenReturn(existing);

        alertService.createAlert(
                AlertType.SHRINKAGE, AlertSeverity.CRITICAL,
                "STOCK", entityId, distributorId,
                0.95, "Updated shrinkage", Map.of("key", "val"));

        // Should NOT create a new alert; should update the existing one
        verify(distributorRepository, never()).findById(any());
        verify(alertRepository).save(existing);
        assertThat(existing.getSeverity()).isEqualTo(AlertSeverity.CRITICAL);
        assertThat(existing.getAnomalyScore()).isEqualTo(0.95);
    }

    @Test
    void createAlert_whenDistributorNotFound_throwsIllegalArgument() {
        when(alertRepository.findExistingOpenAlert(any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        when(distributorRepository.findById(distributorId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> alertService.createAlert(
                AlertType.SHRINKAGE, AlertSeverity.HIGH,
                "STOCK", entityId, distributorId,
                0.8, "desc", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Distributor not found");
    }

    // ── acknowledgeAlert ──────────────────────────────────────────────────

    @Test
    void acknowledgeAlert_setsStatusToAcknowledged() {
        UUID alertId = UUID.randomUUID();
        AnomalyAlert alert = buildAlert(AlertStatus.OPEN);
        when(alertRepository.findById(alertId)).thenReturn(Optional.of(alert));
        when(alertRepository.save(alert)).thenReturn(alert);

        alertService.acknowledgeAlert(alertId, "user@zuqi.com");

        assertThat(alert.getStatus()).isEqualTo(AlertStatus.ACKNOWLEDGED);
        assertThat(alert.getResolvedBy()).isEqualTo("user@zuqi.com");
        verify(alertRepository).save(alert);
    }

    @Test
    void acknowledgeAlert_whenAlertNotFound_throwsIllegalArgument() {
        UUID alertId = UUID.randomUUID();
        when(alertRepository.findById(alertId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> alertService.acknowledgeAlert(alertId, "user"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Alert not found");
    }

    // ── resolveAlert ──────────────────────────────────────────────────────

    @Test
    void resolveAlert_setsStatusToResolved_andAppendsNote() {
        UUID alertId = UUID.randomUUID();
        AnomalyAlert alert = buildAlert(AlertStatus.OPEN);
        alert.setDescription("Original");
        when(alertRepository.findById(alertId)).thenReturn(Optional.of(alert));
        when(alertRepository.save(alert)).thenReturn(alert);

        alertService.resolveAlert(alertId, "admin", "Investigated — false alarm");

        assertThat(alert.getStatus()).isEqualTo(AlertStatus.RESOLVED);
        assertThat(alert.getDescription()).contains("Investigated — false alarm");
        assertThat(alert.getResolvedAt()).isNotNull();
    }

    @Test
    void resolveAlert_withNullNote_doesNotAppend() {
        UUID alertId = UUID.randomUUID();
        AnomalyAlert alert = buildAlert(AlertStatus.OPEN);
        alert.setDescription("Original desc");
        when(alertRepository.findById(alertId)).thenReturn(Optional.of(alert));
        when(alertRepository.save(alert)).thenReturn(alert);

        alertService.resolveAlert(alertId, "admin", null);

        assertThat(alert.getDescription()).isEqualTo("Original desc");
    }

    // ── dismissAlert ──────────────────────────────────────────────────────

    @Test
    void dismissAlert_setsStatusToDismissed() {
        UUID alertId = UUID.randomUUID();
        AnomalyAlert alert = buildAlert(AlertStatus.OPEN);
        when(alertRepository.findById(alertId)).thenReturn(Optional.of(alert));
        when(alertRepository.save(alert)).thenReturn(alert);

        alertService.dismissAlert(alertId, "analyst");

        assertThat(alert.getStatus()).isEqualTo(AlertStatus.DISMISSED);
        assertThat(alert.getResolvedAt()).isNotNull();
    }

    // ── validateTransition ────────────────────────────────────────────────

    @Test
    void acknowledgeAlert_whenAlreadyResolved_throwsIllegalState() {
        UUID alertId = UUID.randomUUID();
        AnomalyAlert alert = buildAlert(AlertStatus.RESOLVED);
        when(alertRepository.findById(alertId)).thenReturn(Optional.of(alert));

        assertThatThrownBy(() -> alertService.acknowledgeAlert(alertId, "user"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot transition");
    }

    @Test
    void resolveAlert_whenAlreadyDismissed_throwsIllegalState() {
        UUID alertId = UUID.randomUUID();
        AnomalyAlert alert = buildAlert(AlertStatus.DISMISSED);
        when(alertRepository.findById(alertId)).thenReturn(Optional.of(alert));

        assertThatThrownBy(() -> alertService.resolveAlert(alertId, "user", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot transition");
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private AnomalyAlert buildAlert(AlertStatus status) {
        AnomalyAlert a = new AnomalyAlert();
        a.setId(UUID.randomUUID());
        a.setStatus(status);
        a.setAnomalyScore(0.8);
        a.setSeverity(AlertSeverity.HIGH);
        a.setAlertType(AlertType.SHRINKAGE);
        a.setEntityType("STOCK");
        a.setEntityId(entityId);
        a.setCreatedAt(LocalDateTime.now());
        return a;
    }
}
