package com.zuqi.ai.model.tuning;

import com.zuqi.ai.synthetic.SyntheticDataConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thin {@code @Async} wrapper around {@link ModelTuningService}.
 *
 * <p>Follows the same Spring AOP proxy pattern as {@code SyntheticGenerationService}:
 * a separate bean is required so that {@code @Async} is honoured when called
 * from a controller or scheduler (self-invocation bypasses the proxy).
 *
 * <p>Job status is tracked in an in-memory {@link ConcurrentHashMap}. This is
 * intentional — tuning is an admin operation and status does not need to survive
 * restarts. The map is bounded: completed entries beyond {@code MAX_HISTORY} are
 * not pruned automatically (admin runs are infrequent).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TuningAsyncService {

    private final ModelTuningService tuningService;

    private final ConcurrentHashMap<UUID, TuningJobStatus> jobs = new ConcurrentHashMap<>();

    // ── Async trigger ─────────────────────────────────────────────────────

    /**
     * Start hyperparameter tuning asynchronously.
     *
     * @param jobId         caller-assigned job UUID (pre-created for polling)
     * @param distributorId distributor scope
     * @param config        synthetic data config used to build the training bundle
     */
    @Async
    public void tuneAsync(UUID jobId, UUID distributorId, SyntheticDataConfig config) {
        log.info("[TuningAsync] Job {} starting for distributor={}", jobId, distributorId);

        jobs.put(jobId, new TuningJobStatus(
                jobId, distributorId, "RUNNING", List.of(), null,
                Instant.now().toEpochMilli(), 0L));

        try {
            ModelTuningService.TuningRunResult result =
                    tuningService.tuneAllModels(distributorId, config);

            jobs.put(jobId, new TuningJobStatus(
                    jobId, distributorId, result.success() ? "COMPLETED" : "COMPLETED_WITH_ERRORS",
                    result.results(), result.errors().isEmpty() ? null : String.join("; ", result.errors()),
                    jobs.get(jobId).startedAt(), result.durationMs()));

            log.info("[TuningAsync] Job {} {} in {}ms — tuned={}",
                    jobId, result.success() ? "COMPLETED" : "COMPLETED_WITH_ERRORS",
                    result.durationMs(), result.results().size());

        } catch (Exception e) {
            long startedAt = jobs.getOrDefault(jobId,
                    new TuningJobStatus(jobId, distributorId, "FAILED",
                            List.of(), null, Instant.now().toEpochMilli(), 0L)).startedAt();
            jobs.put(jobId, new TuningJobStatus(
                    jobId, distributorId, "FAILED", List.of(), e.getMessage(),
                    startedAt, Instant.now().toEpochMilli() - startedAt));

            log.error("[TuningAsync] Job {} FAILED: {}", jobId, e.getMessage(), e);
        }
    }

    // ── Status query ──────────────────────────────────────────────────────

    /**
     * Retrieve the current status of a tuning job.
     *
     * @param jobId the job UUID returned by the trigger endpoint
     * @return current status, or {@code null} if the job is unknown
     */
    public TuningJobStatus getStatus(UUID jobId) {
        return jobs.get(jobId);
    }

    // ── Status record ─────────────────────────────────────────────────────

    /**
     * Snapshot of a tuning job's lifecycle state.
     *
     * @param jobId         the job UUID
     * @param distributorId distributor scope
     * @param status        RUNNING | COMPLETED | COMPLETED_WITH_ERRORS | FAILED
     * @param results       per-model tuning results (empty while RUNNING)
     * @param error         error message (non-null only on FAILED / COMPLETED_WITH_ERRORS)
     * @param startedAt     epoch millis when the job started
     * @param durationMs    elapsed time when finished (0 while RUNNING)
     */
    public record TuningJobStatus(
            UUID               jobId,
            UUID               distributorId,
            String             status,
            List<TuningResult> results,
            String             error,
            long               startedAt,
            long               durationMs) {}
}
