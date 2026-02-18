package com.zuqi.api.controller;

import com.zuqi.ai.anomaly.AlertService;
import com.zuqi.api.dto.ApiResponse;
import com.zuqi.domain.ai.AlertSeverity;
import com.zuqi.domain.ai.AlertStatus;
import com.zuqi.domain.ai.AlertType;
import com.zuqi.domain.ai.AnomalyAlert;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST API for managing AI anomaly alerts.
 *
 * Authorization via Casbin policy.csv (no @PreAuthorize needed).
 *
 * Blueprint reference: plan.md Section 6.3 / implementation_plan.md Phase 4, Step 5
 */
@RestController
@RequestMapping("/v1/ai/anomaly")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "AI - Anomaly Alerts", description = "Manage anomaly alerts from shrinkage, payment, and data quality detection")
public class AiAnomalyController {

    private final AlertService alertService;

    // ── GET /alerts ──────────────────────────────────────────────────────

    @GetMapping("/alerts")
    @Operation(summary = "List anomaly alerts", description = "Paginated alerts for a distributor with optional filters")
    public ResponseEntity<ApiResponse<Page<AnomalyAlert>>> getAlerts(
            @Parameter(required = true)  @RequestParam UUID distributorId,
            @Parameter @RequestParam(required = false) AlertStatus   status,
            @Parameter @RequestParam(required = false) AlertType     alertType,
            @Parameter @RequestParam(required = false) AlertSeverity severity,
            @Parameter @RequestParam(defaultValue = "0")  int page,
            @Parameter @RequestParam(defaultValue = "20") int size) {

        log.info("GET /v1/ai/anomaly/alerts distributor={} status={} type={} severity={}",
                distributorId, status, alertType, severity);
        try {
            PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
            Page<AnomalyAlert> alerts = alertService.getAlerts(distributorId, status, alertType, severity, pageable);
            return ResponseEntity.ok(ApiResponse.success(alerts));
        } catch (Exception e) {
            log.error("Failed to fetch alerts: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to fetch alerts: " + e.getMessage()));
        }
    }

    // ── GET /alerts/{id} ─────────────────────────────────────────────────

    @GetMapping("/alerts/{id}")
    @Operation(summary = "Get a single alert by ID")
    public ResponseEntity<ApiResponse<AnomalyAlert>> getAlert(@PathVariable UUID id) {
        log.info("GET /v1/ai/anomaly/alerts/{}", id);
        try {
            return ResponseEntity.ok(ApiResponse.success(alertService.getAlert(id)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Failed to fetch alert {}: {}", id, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to fetch alert: " + e.getMessage()));
        }
    }

    // ── PUT /alerts/{id}/acknowledge ─────────────────────────────────────

    @PutMapping("/alerts/{id}/acknowledge")
    @Operation(summary = "Acknowledge an alert")
    public ResponseEntity<ApiResponse<AnomalyAlert>> acknowledgeAlert(
            @PathVariable UUID id,
            Authentication authentication) {

        log.info("PUT /v1/ai/anomaly/alerts/{}/acknowledge", id);
        try {
            String by = authentication != null ? authentication.getName() : "system";
            return ResponseEntity.ok(ApiResponse.success("Alert acknowledged",
                    alertService.acknowledgeAlert(id, by)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to acknowledge alert {}: {}", id, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to acknowledge: " + e.getMessage()));
        }
    }

    // ── PUT /alerts/{id}/resolve ──────────────────────────────────────────

    @PutMapping("/alerts/{id}/resolve")
    @Operation(summary = "Resolve an alert")
    public ResponseEntity<ApiResponse<AnomalyAlert>> resolveAlert(
            @PathVariable UUID id,
            @RequestBody(required = false) ResolveRequest body,
            Authentication authentication) {

        log.info("PUT /v1/ai/anomaly/alerts/{}/resolve", id);
        try {
            String by   = authentication != null ? authentication.getName() : "system";
            String note = body != null ? body.resolutionNote() : null;
            return ResponseEntity.ok(ApiResponse.success("Alert resolved",
                    alertService.resolveAlert(id, by, note)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to resolve alert {}: {}", id, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to resolve: " + e.getMessage()));
        }
    }

    // ── PUT /alerts/{id}/dismiss ──────────────────────────────────────────

    @PutMapping("/alerts/{id}/dismiss")
    @Operation(summary = "Dismiss an alert (false positive)")
    public ResponseEntity<ApiResponse<AnomalyAlert>> dismissAlert(
            @PathVariable UUID id,
            Authentication authentication) {

        log.info("PUT /v1/ai/anomaly/alerts/{}/dismiss", id);
        try {
            String by = authentication != null ? authentication.getName() : "system";
            return ResponseEntity.ok(ApiResponse.success("Alert dismissed",
                    alertService.dismissAlert(id, by)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to dismiss alert {}: {}", id, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to dismiss: " + e.getMessage()));
        }
    }

    // ── GET /alerts/summary ───────────────────────────────────────────────

    @GetMapping("/alerts/summary")
    @Operation(summary = "Alert summary counts for dashboard")
    public ResponseEntity<ApiResponse<AlertService.AlertSummary>> getAlertSummary(
            @Parameter(required = true) @RequestParam UUID distributorId) {

        log.info("GET /v1/ai/anomaly/alerts/summary distributor={}", distributorId);
        try {
            return ResponseEntity.ok(ApiResponse.success(alertService.getAlertSummary(distributorId)));
        } catch (Exception e) {
            log.error("Failed to fetch alert summary: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to fetch summary: " + e.getMessage()));
        }
    }

    // ── Inner DTOs ────────────────────────────────────────────────────────

    public record ResolveRequest(String resolutionNote) {}
}
