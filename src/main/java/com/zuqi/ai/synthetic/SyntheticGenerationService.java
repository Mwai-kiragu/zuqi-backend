package com.zuqi.ai.synthetic;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Thin {@code @Async} wrapper around {@link SyntheticDataOrchestrator}.
 *
 * <p>Spring AOP cannot intercept {@code @Async} on a method that is called
 * from within the same bean. This service exists solely to provide a
 * separate Spring-managed proxy so that calling {@link #generateAsync}
 * from a controller or scheduler will run on the async thread pool defined
 * in {@code AsyncConfig}.
 *
 * <p>Lifecycle:
 * <ol>
 *   <li>Caller creates a RUNNING run record via
 *       {@link SyntheticDataOrchestrator#createRunRecord} and receives a {@code runId}.</li>
 *   <li>Caller invokes {@link #generateAsync} — returns immediately.</li>
 *   <li>This method runs on the async executor, delegates to
 *       {@link SyntheticDataOrchestrator#generateBundle}, and marks the run
 *       COMPLETED or FAILED.</li>
 * </ol>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SyntheticGenerationService {

    private final SyntheticDataOrchestrator orchestrator;
    private final SyntheticModelTrainer     modelTrainer;

    /**
     * Asynchronously generate a full synthetic dataset, update the run record,
     * and then train all ML models from the generated bundle (Phase 1.5.14).
     *
     * <p>Model training failures are non-fatal — they are logged and do not mark
     * the generation run itself as FAILED.
     *
     * @param runId  the run record ID to update (already persisted in RUNNING status)
     * @param config generation parameters
     */
    @Async
    public void generateAsync(UUID runId, SyntheticDataConfig config) {
        log.info("[SyntheticGen] Async run {} starting (merchants={}, months={}, seed={})",
                runId, config.merchantCount(), config.historyMonths(), config.randomSeed());

        long startMs = System.currentTimeMillis();
        try {
            SyntheticDataBundle bundle = orchestrator.generateBundle(config);
            long durationMs = System.currentTimeMillis() - startMs;
            orchestrator.completeRun(runId, bundle, durationMs);
            log.info("[SyntheticGen] Async run {} COMPLETED in {} ms — counts: {}",
                    runId, durationMs, bundle.getRecordCounts());

            // Phase 1.5.14: train models from the generated bundle
            try {
                SyntheticModelTrainer.SyntheticTrainingResult tr =
                        modelTrainer.trainAllModels(bundle, config.distributorId());
                log.info("[SyntheticGen] Model training complete — trained={}, errors={}",
                        tr.trainedModelIds().size(), tr.errors().size());
            } catch (Exception e) {
                log.warn("[SyntheticGen] Model training non-fatal error for run {}: {}",
                        runId, e.getMessage(), e);
            }
        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - startMs;
            orchestrator.failRun(runId, e.getMessage(), durationMs);
            log.error("[SyntheticGen] Async run {} FAILED after {} ms", runId, durationMs, e);
        }
    }
}
