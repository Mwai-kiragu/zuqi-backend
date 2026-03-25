package com.zuqi.api.controller;

import com.zuqi.ai.synthetic.SyntheticDataConfig;
import com.zuqi.ai.synthetic.SyntheticDataOrchestrator;
import com.zuqi.ai.synthetic.SyntheticGenerationService;
import com.zuqi.ai.synthetic.dto.SyntheticRunStatusResponse;
import com.zuqi.ai.synthetic.dto.SyntheticSeedRequest;
import com.zuqi.ai.synthetic.dto.SyntheticSeedResponse;
import com.zuqi.api.dto.ApiResponse;
import com.zuqi.domain.ai.AISyntheticRun;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Admin endpoints for triggering and monitoring synthetic data generation.
 *
 * <p>Generation is asynchronous — the POST endpoint returns a run ID immediately,
 * and the GET endpoint is polled for status.
 *
 * <h3>Access control</h3>
 * SUPER_ADMIN only (covered by the existing Casbin wildcard rule
 * {@code p, SUPER_ADMIN, /v1/*, .*}).
 *
 * Blueprint reference: implementation_plan.md §1.5.10
 */
@RestController
@RequestMapping("/v1/ai/admin/seed-synthetic")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "AI Admin — Synthetic Data",
     description = "Trigger and monitor synthetic training data generation")
public class SyntheticSeedController {

    private final SyntheticDataOrchestrator  orchestrator;
    private final SyntheticGenerationService generationService;

    // -------------------------------------------------------------------------
    // POST /v1/ai/admin/seed-synthetic/{distributorId}
    // -------------------------------------------------------------------------

    /**
     * Trigger full synthetic dataset generation for a distributor.
     *
     * <p>The run starts immediately on the async executor. The caller receives the
     * {@code runId} and should poll the status endpoint until the run completes.
     *
     * @param distributorId UUID of the target distributor
     * @param request       optional overrides for merchantCount, historyMonths, randomSeed
     * @param principal     authenticated user triggering the run
     * @return 202 Accepted with run ID and status poll URL
     */
    @PostMapping("/{distributorId}")
    @Operation(
            summary = "Start synthetic data generation",
            description = "Triggers async generation of a full synthetic training dataset for the given distributor. "
                    + "Returns immediately with a run ID for status polling.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<SyntheticSeedResponse>> triggerGeneration(
            @PathVariable UUID distributorId,
            @RequestBody(required = false) SyntheticSeedRequest request,
            @RequestParam(required = false) List<String> models,
            Principal principal) {

        if (request == null) {
            request = new SyntheticSeedRequest(null, null, null);
        }

        int  merchantCount = request.effectiveMerchantCount();
        int  historyMonths = request.effectiveHistoryMonths();
        long seed          = request.effectiveSeed();

        SyntheticDataConfig config = new SyntheticDataConfig(
                distributorId,
                merchantCount,
                historyMonths,
                seed,
                SyntheticDataConfig.DEFAULT_ARCHETYPE_RATIOS);

        Set<String> modelFilter = (models != null && !models.isEmpty())
                ? new HashSet<>(models) : Set.of();

        String triggeredBy = (principal != null) ? principal.getName() : "system";
        AISyntheticRun run = orchestrator.createRunRecord(distributorId, config, triggeredBy);
        UUID runId = run.getId();

        log.info("[SyntheticSeed] Run {} started by {} — distributor={}, merchants={}, months={}, seed={}, filter={}",
                runId, triggeredBy, distributorId, merchantCount, historyMonths, seed,
                modelFilter.isEmpty() ? "ALL" : modelFilter);

        generationService.generateAsync(runId, config, modelFilter);

        String statusUrl = "/v1/ai/admin/seed-synthetic/" + runId + "/status";
        SyntheticSeedResponse response = new SyntheticSeedResponse(
                runId, distributorId, merchantCount, historyMonths, seed, statusUrl);

        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success("Synthetic generation started", response));
    }

    // -------------------------------------------------------------------------
    // GET /v1/ai/admin/seed-synthetic/{runId}/status
    // -------------------------------------------------------------------------

    /**
     * Poll the status of a synthetic generation run.
     *
     * @param runId UUID of the run record returned by the POST endpoint
     * @return 200 with status details, or 404 if the run does not exist
     */
    @GetMapping("/{runId}/status")
    @Operation(
            summary = "Get synthetic generation run status",
            description = "Returns the current status and (when completed) record counts for a generation run.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<SyntheticRunStatusResponse>> getRunStatus(
            @PathVariable UUID runId) {

        return orchestrator.getRunStatus(runId)
                .map(run -> {
                    SyntheticRunStatusResponse status = toStatusResponse(run);
                    return ResponseEntity.ok(ApiResponse.success(status));
                })
                .orElseGet(() -> ResponseEntity
                        .status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Run not found: " + runId)));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private SyntheticRunStatusResponse toStatusResponse(AISyntheticRun run) {
        return new SyntheticRunStatusResponse(
                run.getId(),
                run.getStatus() != null ? run.getStatus().name() : null,
                run.getMerchantCount(),
                run.getHistoryMonths(),
                run.getRandomSeed(),
                run.getStartedAt(),
                run.getCompletedAt(),
                run.getDurationMs(),
                run.getRecordsGenerated(),
                run.getErrorMessage()
        );
    }
}
