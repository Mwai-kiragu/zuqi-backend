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
import java.util.List;
import java.util.Set;
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
     * Trigger hyperparameter tuning for all 15 ML models (or a named subset).
     *
     * <p>The job starts on the async executor and returns immediately.
     * Poll {@code GET /v1/ai/admin/tune/{jobId}/status} until {@code status != "RUNNING"}.
     *
     * @param distributorId UUID of the distributor to scope tuning to
     * @param merchantCount optional override for synthetic merchant count (default 500)
     * @param models        optional comma-separated list of model names to tune;
     *                      omit to tune all 15 models
     * @param principal     authenticated user triggering the run
     * @return 202 Accepted with a {@code jobId} for status polling
     */
    @PostMapping("/{distributorId}")
    @Operation(
            summary  = "Start hyperparameter tuning",
            description = "Triggers async k-fold CV tuning for ML models using a synthetic "
                    + "training bundle. Pass ?models=credit_classifier,churn_predictor to tune "
                    + "a subset. Returns immediately with a jobId for status polling.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<TuningJobResponse>> startTuning(
            @PathVariable UUID distributorId,
            @RequestParam(required = false) Integer merchantCount,
            @RequestParam(required = false) List<String> models,
            Principal principal) {

        int count = (merchantCount != null && merchantCount > 0) ? merchantCount : DEFAULT_MERCHANT_COUNT;
        Set<String> modelFilter = (models != null && !models.isEmpty()) ? Set.copyOf(models) : Set.of();

        SyntheticDataConfig config = new SyntheticDataConfig(
                distributorId,
                count,
                DEFAULT_HISTORY_MONTHS,
                TUNING_SEED,
                SyntheticDataConfig.DEFAULT_ARCHETYPE_RATIOS);

        UUID jobId = UUID.randomUUID();
        String triggeredBy = (principal != null) ? principal.getName() : "system";

        log.info("[TuningController] Tuning job {} started by {} — distributor={}, merchants={}, filter={}",
                jobId, triggeredBy, distributorId, count,
                modelFilter.isEmpty() ? "all" : modelFilter);

        tuningAsyncService.tuneAsync(jobId, distributorId, config, modelFilter);

        String statusUrl = "/v1/ai/admin/tune/" + jobId + "/status";
        List<String> filterList = modelFilter.isEmpty() ? List.of() : List.copyOf(modelFilter);
        TuningJobResponse response = new TuningJobResponse(jobId, distributorId, count, filterList, statusUrl);

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
     * @return 200 with current status; if the job is unknown returns 200 with status=UNKNOWN
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
            // Return 200 with UNKNOWN so polling scripts don't fail on curl -sf
            TuningStatusResponse unknown = new TuningStatusResponse(
                    jobId, null, "UNKNOWN", List.of(), "Job not found (may be initializing or app restarted)", 0L, 0L);
            return ResponseEntity.ok(ApiResponse.success(unknown));
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
