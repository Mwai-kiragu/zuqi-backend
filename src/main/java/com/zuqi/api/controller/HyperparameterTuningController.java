package com.zuqi.api.controller;

import com.zuqi.ai.model.tuning.TuningAsyncService;
import com.zuqi.ai.model.tuning.dto.TuningJobResponse;
import com.zuqi.ai.model.tuning.dto.TuningStatusResponse;
import com.zuqi.ai.synthetic.SyntheticDataConfig;
import com.zuqi.api.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.UUID;

/**
 * Admin endpoints for triggering and monitoring hyperparameter tuning runs.
 *
 * <p>Tuning is asynchronous and computationally expensive. The POST endpoint
 * returns a {@code jobId} immediately; the GET endpoint is polled for status.
 *
 * <h3>Access control</h3>
 * SUPER_ADMIN only (covered by the existing Casbin wildcard rule
 * {@code p, SUPER_ADMIN, /v1/*, .*}).
 *
 * Blueprint reference: implementation_plan.md §1.5 hyperparameter tuning
 */
@RestController
@RequestMapping("/v1/ai/admin/tune")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "AI Admin — Hyperparameter Tuning",
     description = "Trigger and monitor ML model hyperparameter tuning runs")
public class HyperparameterTuningController {

    /** Default merchant count for the synthetic bundle used in tuning. */
    private static final int DEFAULT_MERCHANT_COUNT = 500;
    /** Default history months for the synthetic bundle. */
    private static final int DEFAULT_HISTORY_MONTHS = 12;
    /** Fixed random seed for reproducible tuning bundles. */
    private static final long TUNING_SEED = 42L;

    private final TuningAsyncService tuningAsyncService;

    // -------------------------------------------------------------------------
    // POST /v1/ai/admin/tune/{distributorId}
    // -------------------------------------------------------------------------

    /**
     * Trigger a full hyperparameter tuning run for all 9 ML models.
     *
     * <p>The job starts on the async executor and returns immediately.
     * Poll {@code GET /v1/ai/admin/tune/{jobId}/status} until {@code status != "RUNNING"}.
     *
     * @param distributorId UUID of the distributor to scope tuning to
     * @param merchantCount optional override for synthetic merchant count (default 500)
     * @param principal     authenticated user triggering the run
     * @return 202 Accepted with a {@code jobId} for status polling
     */
    @PostMapping("/{distributorId}")
    @Operation(
            summary  = "Start hyperparameter tuning",
            description = "Triggers async k-fold CV tuning for all 9 ML models using a synthetic "
                    + "training bundle. Returns immediately with a jobId for status polling.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<TuningJobResponse>> startTuning(
            @PathVariable UUID distributorId,
            @RequestParam(required = false) Integer merchantCount,
            Principal principal) {

        int count = (merchantCount != null && merchantCount > 0) ? merchantCount : DEFAULT_MERCHANT_COUNT;

        SyntheticDataConfig config = new SyntheticDataConfig(
                distributorId,
                count,
                DEFAULT_HISTORY_MONTHS,
                TUNING_SEED,
                SyntheticDataConfig.DEFAULT_ARCHETYPE_RATIOS);

        UUID jobId = UUID.randomUUID();
        String triggeredBy = (principal != null) ? principal.getName() : "system";

        log.info("[TuningController] Tuning job {} started by {} — distributor={}, merchants={}",
                jobId, triggeredBy, distributorId, count);

        tuningAsyncService.tuneAsync(jobId, distributorId, config);

        String statusUrl = "/v1/ai/admin/tune/" + jobId + "/status";
        TuningJobResponse response = new TuningJobResponse(jobId, distributorId, count, statusUrl);

        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success("Hyperparameter tuning started", response));
    }

    // -------------------------------------------------------------------------
    // GET /v1/ai/admin/tune/{jobId}/status
    // -------------------------------------------------------------------------

    /**
     * Poll the status of a tuning job.
     *
     * @param jobId UUID returned by the POST endpoint
     * @return 200 with current status, or 404 if the job is unknown
     */
    @GetMapping("/{jobId}/status")
    @Operation(
            summary  = "Get tuning job status",
            description = "Returns the current status of a hyperparameter tuning job. "
                    + "Poll until status is COMPLETED, COMPLETED_WITH_ERRORS, or FAILED.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<TuningStatusResponse>> getTuningStatus(
            @PathVariable UUID jobId) {

        TuningAsyncService.TuningJobStatus job = tuningAsyncService.getStatus(jobId);
        if (job == null) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Tuning job not found: " + jobId));
        }

        TuningStatusResponse response = new TuningStatusResponse(
                job.jobId(),
                job.distributorId(),
                job.status(),
                job.results(),
                job.error(),
                job.startedAt(),
                job.durationMs());

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
