package com.zuqi.ai.model.tuning;

import com.zuqi.ai.synthetic.SyntheticDataConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TuningAsyncService}.
 *
 * The async annotation is not activated in unit tests — {@code tuneAsync} is
 * called synchronously (no Spring context) so we can verify status transitions.
 */
@ExtendWith(MockitoExtension.class)
class TuningAsyncServiceTest {

    @Mock private ModelTuningService tuningService;

    private TuningAsyncService asyncService;
    private final UUID distributorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        asyncService = new TuningAsyncService(tuningService);
    }

    // ── Status before job starts ──────────────────────────────────────────

    @Test
    void getStatus_unknownJobId_returnsNull() {
        assertThat(asyncService.getStatus(UUID.randomUUID())).isNull();
    }

    // ── Successful run ────────────────────────────────────────────────────

    @Test
    void tuneAsync_successfulRun_statusIsCompleted() {
        TuningResult result = new TuningResult(
                "credit_classifier", UUID.randomUUID(),
                Map.of("num_rounds", 100), 0.85, "macro_f1", 5, 5);

        ModelTuningService.TuningRunResult runResult =
                new ModelTuningService.TuningRunResult(List.of(result), List.of(), true, 1000L);

        when(tuningService.tuneAllModels(any(), any())).thenReturn(runResult);

        UUID jobId = UUID.randomUUID();
        asyncService.tuneAsync(jobId, distributorId, dummyConfig());

        TuningAsyncService.TuningJobStatus status = asyncService.getStatus(jobId);
        assertThat(status).isNotNull();
        assertThat(status.status()).isEqualTo("COMPLETED");
        assertThat(status.results()).containsExactly(result);
        assertThat(status.error()).isNull();
        assertThat(status.durationMs()).isEqualTo(1000L);
    }

    @Test
    void tuneAsync_successfulRun_jobIdIsPreserved() {
        when(tuningService.tuneAllModels(any(), any())).thenReturn(
                new ModelTuningService.TuningRunResult(List.of(), List.of(), true, 0L));

        UUID jobId = UUID.randomUUID();
        asyncService.tuneAsync(jobId, distributorId, dummyConfig());

        assertThat(asyncService.getStatus(jobId).jobId()).isEqualTo(jobId);
        assertThat(asyncService.getStatus(jobId).distributorId()).isEqualTo(distributorId);
    }

    // ── Run with errors ───────────────────────────────────────────────────

    @Test
    void tuneAsync_partialFailure_statusIsCompletedWithErrors() {
        ModelTuningService.TuningRunResult runResult =
                new ModelTuningService.TuningRunResult(
                        List.of(), List.of("credit_classifier: error msg"), false, 500L);

        when(tuningService.tuneAllModels(any(), any())).thenReturn(runResult);

        UUID jobId = UUID.randomUUID();
        asyncService.tuneAsync(jobId, distributorId, dummyConfig());

        TuningAsyncService.TuningJobStatus status = asyncService.getStatus(jobId);
        assertThat(status.status()).isEqualTo("COMPLETED_WITH_ERRORS");
        assertThat(status.error()).contains("credit_classifier");
    }

    // ── Fatal exception ───────────────────────────────────────────────────

    @Test
    void tuneAsync_fatalException_statusIsFailed() {
        when(tuningService.tuneAllModels(any(), any()))
                .thenThrow(new RuntimeException("bundle generation failed"));

        UUID jobId = UUID.randomUUID();
        asyncService.tuneAsync(jobId, distributorId, dummyConfig());

        TuningAsyncService.TuningJobStatus status = asyncService.getStatus(jobId);
        assertThat(status.status()).isEqualTo("FAILED");
        assertThat(status.error()).contains("bundle generation failed");
        assertThat(status.results()).isEmpty();
    }

    // ── TuningJobStatus record ────────────────────────────────────────────

    @Test
    void tuningJobStatus_storesAllFields() {
        UUID jobId = UUID.randomUUID();
        TuningResult r = new TuningResult("m", UUID.randomUUID(), Map.of(), 0.5, "f1", 5, 5);

        TuningAsyncService.TuningJobStatus status = new TuningAsyncService.TuningJobStatus(
                jobId, distributorId, "COMPLETED", List.of(r), null, 1000L, 2000L);

        assertThat(status.jobId()).isEqualTo(jobId);
        assertThat(status.distributorId()).isEqualTo(distributorId);
        assertThat(status.status()).isEqualTo("COMPLETED");
        assertThat(status.results()).containsExactly(r);
        assertThat(status.error()).isNull();
        assertThat(status.startedAt()).isEqualTo(1000L);
        assertThat(status.durationMs()).isEqualTo(2000L);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private SyntheticDataConfig dummyConfig() {
        return new SyntheticDataConfig(
                distributorId, 50, 6, 42L,
                SyntheticDataConfig.DEFAULT_ARCHETYPE_RATIOS);
    }
}
