package com.zuqi.ai.agent.tools;

import com.zuqi.domain.ai.AlertSeverity;
import com.zuqi.domain.ai.AlertStatus;
import com.zuqi.domain.ai.AlertType;
import com.zuqi.domain.ai.AnomalyAlert;
import com.zuqi.repository.AnomalyAlertRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AnomalyAlertsTool}.
 *
 * Covers: happy path with alerts, empty alerts, invalid UUID, repository exception.
 */
@ExtendWith(MockitoExtension.class)
class AnomalyAlertsToolTest {

    @Mock private AnomalyAlertRepository anomalyAlertRepository;

    @InjectMocks
    private AnomalyAlertsTool tool;

    // ── happy paths ───────────────────────────────────────────────────────

    @Test
    void getAnomalyAlerts_withOpenAlerts_returnsJsonWithSeverityCounts() {
        UUID distributorId = UUID.randomUUID();

        AnomalyAlert critical = buildAlert(AlertSeverity.CRITICAL, AlertType.SHRINKAGE);
        AnomalyAlert high     = buildAlert(AlertSeverity.HIGH,     AlertType.PAYMENT_ANOMALY);

        when(anomalyAlertRepository.findByDistributorIdAndStatus(distributorId, AlertStatus.OPEN))
                .thenReturn(List.of(critical, high));
        when(anomalyAlertRepository.findByDistributorIdAndStatus(distributorId, AlertStatus.ACKNOWLEDGED))
                .thenReturn(List.of());

        String result = tool.getAnomalyAlerts(distributorId.toString(), "30");

        assertThat(result).contains("\"tool\": \"AnomalyAlerts\"");
        assertThat(result).contains("\"totalOpen\": 2");
        assertThat(result).contains("\"critical\": 1");
        assertThat(result).contains("\"high\": 1");
    }

    @Test
    void getAnomalyAlerts_withNoOpenAlerts_returnsZeroCounts() {
        UUID distributorId = UUID.randomUUID();

        when(anomalyAlertRepository.findByDistributorIdAndStatus(any(), eq(AlertStatus.OPEN)))
                .thenReturn(List.of());
        when(anomalyAlertRepository.findByDistributorIdAndStatus(any(), eq(AlertStatus.ACKNOWLEDGED)))
                .thenReturn(List.of());

        String result = tool.getAnomalyAlerts(distributorId.toString(), "30");

        assertThat(result).contains("\"totalOpen\": 0");
        assertThat(result).doesNotContain("\"error\"");
    }

    @Test
    void getAnomalyAlerts_withAcknowledgedAlerts_includesAcknowledgedCount() {
        UUID distributorId = UUID.randomUUID();

        when(anomalyAlertRepository.findByDistributorIdAndStatus(any(), eq(AlertStatus.OPEN)))
                .thenReturn(List.of());
        when(anomalyAlertRepository.findByDistributorIdAndStatus(any(), eq(AlertStatus.ACKNOWLEDGED)))
                .thenReturn(List.of(buildAlert(AlertSeverity.HIGH, AlertType.SHRINKAGE)));

        String result = tool.getAnomalyAlerts(distributorId.toString(), "30");

        assertThat(result).contains("\"totalAcknowledged\": 1");
    }

    // ── error paths ───────────────────────────────────────────────────────

    @Test
    void getAnomalyAlerts_whenInvalidUuid_returnsErrorJson() {
        String result = tool.getAnomalyAlerts("not-a-valid-uuid", "30");

        assertThat(result).contains("\"error\"");
        verifyNoInteractions(anomalyAlertRepository);
    }

    @Test
    void getAnomalyAlerts_whenRepositoryThrows_returnsErrorJson() {
        when(anomalyAlertRepository.findByDistributorIdAndStatus(any(), any()))
                .thenThrow(new RuntimeException("DB connection lost"));

        String result = tool.getAnomalyAlerts(UUID.randomUUID().toString(), "30");

        assertThat(result).contains("\"error\"");
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private AnomalyAlert buildAlert(AlertSeverity severity, AlertType type) {
        AnomalyAlert alert = new AnomalyAlert();
        alert.setId(UUID.randomUUID());
        alert.setStatus(AlertStatus.OPEN);
        alert.setSeverity(severity);
        alert.setAlertType(type);
        alert.setCreatedAt(LocalDateTime.now());
        alert.setDescription("Test alert description");
        return alert;
    }
}
