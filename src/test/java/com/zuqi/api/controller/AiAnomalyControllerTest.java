package com.zuqi.api.controller;

import com.zuqi.ai.anomaly.AlertService;
import com.zuqi.domain.ai.AlertSeverity;
import com.zuqi.domain.ai.AlertStatus;
import com.zuqi.domain.ai.AlertType;
import com.zuqi.domain.ai.AnomalyAlert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AiAnomalyController}.
 *
 * Verifies HTTP response codes and delegation behaviour for all 6 endpoints.
 */
@ExtendWith(MockitoExtension.class)
class AiAnomalyControllerTest {

    @Mock private AlertService    alertService;
    @Mock private Authentication  authentication;

    @InjectMocks
    private AiAnomalyController controller;

    // ── GET /alerts ───────────────────────────────────────────────────────

    @Test
    void getAlerts_returnsPageOf200() {
        UUID distributorId = UUID.randomUUID();
        Page<AnomalyAlert> page = new PageImpl<>(List.of(buildAlert()));
        when(alertService.getAlerts(eq(distributorId), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(page);

        ResponseEntity<?> response = controller.getAlerts(distributorId, null, null, null, 0, 20);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getAlerts_withFilters_delegates() {
        UUID distributorId = UUID.randomUUID();
        Page<AnomalyAlert> page = new PageImpl<>(List.of());
        when(alertService.getAlerts(any(), eq(AlertStatus.OPEN), eq(AlertType.SHRINKAGE),
                eq(AlertSeverity.HIGH), any(Pageable.class))).thenReturn(page);

        ResponseEntity<?> response = controller.getAlerts(
                distributorId, AlertStatus.OPEN, AlertType.SHRINKAGE, AlertSeverity.HIGH, 0, 20);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getAlerts_whenServiceThrows_returns500() {
        when(alertService.getAlerts(any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("DB error"));

        ResponseEntity<?> response = controller.getAlerts(UUID.randomUUID(), null, null, null, 0, 20);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // ── GET /alerts/{id} ─────────────────────────────────────────────────

    @Test
    void getAlert_whenFound_returns200() {
        UUID alertId = UUID.randomUUID();
        when(alertService.getAlert(alertId)).thenReturn(buildAlert());

        ResponseEntity<?> response = controller.getAlert(alertId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getAlert_whenNotFound_returns404() {
        UUID alertId = UUID.randomUUID();
        when(alertService.getAlert(alertId))
                .thenThrow(new IllegalArgumentException("Alert not found"));

        ResponseEntity<?> response = controller.getAlert(alertId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── PUT /alerts/{id}/acknowledge ──────────────────────────────────────

    @Test
    void acknowledgeAlert_returns200() {
        UUID alertId = UUID.randomUUID();
        when(authentication.getName()).thenReturn("admin");
        when(alertService.acknowledgeAlert(alertId, "admin")).thenReturn(buildAlert());

        ResponseEntity<?> response = controller.acknowledgeAlert(alertId, authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void acknowledgeAlert_whenAlreadyResolved_returns400() {
        UUID alertId = UUID.randomUUID();
        when(authentication.getName()).thenReturn("admin");
        when(alertService.acknowledgeAlert(any(), any()))
                .thenThrow(new IllegalStateException("Cannot transition"));

        ResponseEntity<?> response = controller.acknowledgeAlert(alertId, authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void acknowledgeAlert_whenAlertNotFound_returns404() {
        UUID alertId = UUID.randomUUID();
        when(authentication.getName()).thenReturn("admin");
        when(alertService.acknowledgeAlert(any(), any()))
                .thenThrow(new IllegalArgumentException("Alert not found"));

        ResponseEntity<?> response = controller.acknowledgeAlert(alertId, authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── PUT /alerts/{id}/resolve ──────────────────────────────────────────

    @Test
    void resolveAlert_withNote_returns200() {
        UUID alertId = UUID.randomUUID();
        when(authentication.getName()).thenReturn("user");
        when(alertService.resolveAlert(eq(alertId), eq("user"), eq("root cause fixed")))
                .thenReturn(buildAlert());

        AiAnomalyController.ResolveRequest body = new AiAnomalyController.ResolveRequest("root cause fixed");
        ResponseEntity<?> response = controller.resolveAlert(alertId, body, authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void resolveAlert_withNullBody_delegates200() {
        UUID alertId = UUID.randomUUID();
        when(authentication.getName()).thenReturn("user");
        when(alertService.resolveAlert(eq(alertId), eq("user"), isNull()))
                .thenReturn(buildAlert());

        ResponseEntity<?> response = controller.resolveAlert(alertId, null, authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ── PUT /alerts/{id}/dismiss ──────────────────────────────────────────

    @Test
    void dismissAlert_returns200() {
        UUID alertId = UUID.randomUUID();
        when(authentication.getName()).thenReturn("analyst");
        when(alertService.dismissAlert(alertId, "analyst")).thenReturn(buildAlert());

        ResponseEntity<?> response = controller.dismissAlert(alertId, authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ── GET /alerts/summary ───────────────────────────────────────────────

    @Test
    void getAlertSummary_returns200() {
        UUID distributorId = UUID.randomUUID();
        AlertService.AlertSummary summary = new AlertService.AlertSummary(10L, 2L, 3L, 4L, 1L);
        when(alertService.getAlertSummary(distributorId)).thenReturn(summary);

        ResponseEntity<?> response = controller.getAlertSummary(distributorId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getAlertSummary_whenServiceThrows_returns500() {
        when(alertService.getAlertSummary(any()))
                .thenThrow(new RuntimeException("DB unavailable"));

        ResponseEntity<?> response = controller.getAlertSummary(UUID.randomUUID());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // ── helper ────────────────────────────────────────────────────────────

    private AnomalyAlert buildAlert() {
        AnomalyAlert a = new AnomalyAlert();
        a.setId(UUID.randomUUID());
        a.setStatus(AlertStatus.OPEN);
        a.setSeverity(AlertSeverity.HIGH);
        a.setAlertType(AlertType.SHRINKAGE);
        a.setEntityType("STOCK");
        a.setEntityId(UUID.randomUUID());
        return a;
    }
}
