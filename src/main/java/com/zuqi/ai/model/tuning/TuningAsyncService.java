package com.zuqi.ai.model.tuning;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zuqi.ai.synthetic.SyntheticDataConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
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

    private static final String REDIS_KEY_PREFIX = "tuning:job:";
    private static final Duration REDIS_TTL      = Duration.ofHours(24);

    private final ModelTuningService          tuningService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper                objectMapper;

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

        TuningJobStatus running = new TuningJobStatus(
                jobId, distributorId, "RUNNING", List.of(), null,
                Instant.now().toEpochMilli(), 0L);
        jobs.put(jobId, running);
        persistToRedis(jobId, running);

        try {
            ModelTuningService.TuningRunResult result =
                    tuningService.tuneAllModels(distributorId, config);

            TuningJobStatus done = new TuningJobStatus(
                    jobId, distributorId, result.success() ? "COMPLETED" : "COMPLETED_WITH_ERRORS",
                    result.results(), result.errors().isEmpty() ? null : String.join("; ", result.errors()),
                    jobs.get(jobId).startedAt(), result.durationMs());
            jobs.put(jobId, done);
            persistToRedis(jobId, done);

            log.info("[TuningAsync] Job {} {} in {}ms — tuned={}",
                    jobId, result.success() ? "COMPLETED" : "COMPLETED_WITH_ERRORS",
                    result.durationMs(), result.results().size());

        } catch (Exception e) {
            long startedAt = jobs.getOrDefault(jobId,
                    new TuningJobStatus(jobId, distributorId, "FAILED",
                            List.of(), null, Instant.now().toEpochMilli(), 0L)).startedAt();
            TuningJobStatus failed = new TuningJobStatus(
                    jobId, distributorId, "FAILED", List.of(), e.getMessage(),
                    startedAt, Instant.now().toEpochMilli() - startedAt);
            jobs.put(jobId, failed);
            persistToRedis(jobId, failed);

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
        TuningJobStatus inMemory = jobs.get(jobId);
        if (inMemory != null) return inMemory;
        return loadFromRedis(jobId);
    }

    // ── Redis persistence ─────────────────────────────────────────────────

    private void persistToRedis(UUID jobId, TuningJobStatus status) {
        try {
            String key = REDIS_KEY_PREFIX + jobId;
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(status), REDIS_TTL);
        } catch (Exception e) {
            log.warn("[TuningAsync] Failed to persist job {} status to Redis: {}", jobId, e.getMessage());
        }
    }

    private TuningJobStatus loadFromRedis(UUID jobId) {
        try {
            String key = REDIS_KEY_PREFIX + jobId;
            Object raw = redisTemplate.opsForValue().get(key);
            if (raw == null) return null;
            String json = raw instanceof String s ? s : objectMapper.writeValueAsString(raw);
            TuningJobStatus status = objectMapper.readValue(json,
                    new TypeReference<TuningJobStatus>() {});
            jobs.put(jobId, status); // re-populate in-memory cache
            return status;
        } catch (Exception e) {
            log.warn("[TuningAsync] Failed to load job {} status from Redis: {}", jobId, e.getMessage());
            return null;
        }
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
