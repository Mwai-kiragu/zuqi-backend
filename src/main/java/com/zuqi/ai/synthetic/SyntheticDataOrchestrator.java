package com.zuqi.ai.synthetic;

import com.zuqi.ai.synthetic.dto.*;
import com.zuqi.ai.synthetic.generators.SyntheticBankStatementGenerator;
import com.zuqi.ai.synthetic.generators.SyntheticCashFlowGenerator;
import com.zuqi.ai.synthetic.generators.SyntheticExpiryBatchGenerator;
import com.zuqi.ai.synthetic.generators.SyntheticExpiryBatchGenerator.SyntheticExpiryBatch;

import com.zuqi.ai.synthetic.generators.*;
import com.zuqi.ai.synthetic.generators.OrderHistoryGenerator.OrderHistoryResult;
import com.zuqi.domain.ai.AISyntheticRun;
import com.zuqi.domain.ai.SyntheticRunStatus;
import com.zuqi.domain.ai.SyntheticRunType;
import com.zuqi.domain.distributor.Distributor;
import com.zuqi.repository.AISyntheticRunRepository;
import com.zuqi.repository.DistributorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates the full synthetic data generation pipeline for a distributor.
 *
 * <p>Calls all six generators in dependency order, assembles the results into a
 * {@link SyntheticDataBundle}, and manages the lifecycle of the {@code ai_synthetic_runs}
 * audit record.
 *
 * <p>The actual generation work is synchronous and can be called directly in tests
 * via {@link #generateBundle}. Asynchronous execution is handled by
 * {@link SyntheticGenerationService}, which wraps this service with {@code @Async}.
 *
 * <h3>Generation order and dependencies</h3>
 * <ol>
 *   <li>Merchants (no deps)</li>
 *   <li>Orders + items (depends on merchants)</li>
 *   <li>Payments (depends on orders + merchants)</li>
 *   <li>Inventory movements (depends on orders)</li>
 *   <li>Rep activities (depends on merchants)</li>
 *   <li>Credit evaluations (depends on merchants + payments)</li>
 * </ol>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SyntheticDataOrchestrator {

    private final MerchantProfileGenerator       merchantProfileGenerator;
    private final OrderHistoryGenerator          orderHistoryGenerator;
    private final PaymentBehaviorGenerator       paymentBehaviorGenerator;
    private final InventoryMovementGenerator     inventoryMovementGenerator;
    private final SalesRepActivityGenerator      salesRepActivityGenerator;
    private final CreditHistoryGenerator         creditHistoryGenerator;
    private final SyntheticExpiryBatchGenerator  expiryBatchGenerator;
    private final SyntheticBankStatementGenerator bankStatementGenerator;
    private final SyntheticCashFlowGenerator     cashFlowGenerator;
    private final AISyntheticRunRepository       syntheticRunRepository;
    private final DistributorRepository          distributorRepository;

    // -------------------------------------------------------------------------
    // Run record management
    // -------------------------------------------------------------------------

    /**
     * Create and persist an {@code ai_synthetic_runs} record in RUNNING status.
     * Called before kicking off async generation so the caller has a run ID immediately.
     */
    public AISyntheticRun createRunRecord(UUID distributorId, SyntheticDataConfig config,
                                          String triggeredBy) {
        Distributor distributor = (distributorId != null)
                ? distributorRepository.findById(distributorId).orElse(null)
                : null;

        AISyntheticRun run = AISyntheticRun.builder()
                .distributor(distributor)
                .runType(SyntheticRunType.FULL_SEED)
                .randomSeed(config.randomSeed())
                .merchantCount(config.merchantCount())
                .historyMonths(config.historyMonths())
                .archetypeRatios(buildArchetypeRatiosMap(config))
                .configSnapshot(buildConfigSnapshot(config))
                .triggeredBy(triggeredBy)
                .status(SyntheticRunStatus.RUNNING)
                .startedAt(LocalDateTime.now())
                .build();

        return syntheticRunRepository.save(run);
    }

    /** Mark a run as COMPLETED and persist the generated record counts and duration. */
    public void completeRun(UUID runId, SyntheticDataBundle bundle, long durationMs) {
        syntheticRunRepository.findById(runId).ifPresent(run -> {
            run.setStatus(SyntheticRunStatus.COMPLETED);
            run.setCompletedAt(LocalDateTime.now());
            run.setDurationMs(durationMs);
            run.setRecordsGenerated(new HashMap<>(bundle.getRecordCounts()));
            syntheticRunRepository.save(run);
        });
    }

    /** Mark a run as FAILED with an error message. */
    public void failRun(UUID runId, String errorMessage, long durationMs) {
        syntheticRunRepository.findById(runId).ifPresent(run -> {
            run.setStatus(SyntheticRunStatus.FAILED);
            run.setErrorMessage(errorMessage);
            run.setCompletedAt(LocalDateTime.now());
            run.setDurationMs(durationMs);
            syntheticRunRepository.save(run);
        });
    }

    /** Look up a run record by ID. */
    public Optional<AISyntheticRun> getRunStatus(UUID runId) {
        return syntheticRunRepository.findById(runId);
    }

    // -------------------------------------------------------------------------
    // Core generation pipeline (synchronous)
    // -------------------------------------------------------------------------

    /**
     * Execute the full six-step generation pipeline synchronously and return the bundle.
     *
     * <p>This method is intentionally not {@code @Async} — it is called from
     * {@link SyntheticGenerationService} which handles the async wrapper.
     * Tests can call this method directly.
     *
     * @param config     generation parameters
     * @return complete in-memory dataset
     */
    public SyntheticDataBundle generateBundle(SyntheticDataConfig config) {
        long pipelineStart = System.currentTimeMillis();
        log.info("[Synthetic] Pipeline start — {} merchants × {} months (seed={})",
                config.merchantCount(), config.historyMonths(), config.randomSeed());

        // Step 1: merchant profiles
        long t = System.currentTimeMillis();
        List<SyntheticMerchant> merchants = merchantProfileGenerator.generate(config);
        log.info("[Synthetic] 1/6 merchants ({}) — {} ms",
                merchants.size(), System.currentTimeMillis() - t);

        // Step 2: order history
        t = System.currentTimeMillis();
        OrderHistoryResult orderResult = orderHistoryGenerator.generate(merchants, config);
        log.info("[Synthetic] 2/6 orders ({}) items ({}) — {} ms",
                orderResult.orders().size(), orderResult.items().size(),
                System.currentTimeMillis() - t);

        // Step 3: payments
        t = System.currentTimeMillis();
        List<SyntheticPayment> payments =
                paymentBehaviorGenerator.generate(orderResult.orders(), merchants, config);
        log.info("[Synthetic] 3/6 payments ({}) — {} ms",
                payments.size(), System.currentTimeMillis() - t);

        // Step 4: inventory movements (parallel-eligible with steps 5–6 but kept sequential
        //         for determinism and simplicity)
        t = System.currentTimeMillis();
        List<SyntheticInventoryMovement> movements =
                inventoryMovementGenerator.generate(orderResult.orders(), config);
        log.info("[Synthetic] 4/6 inventory movements ({}) — {} ms",
                movements.size(), System.currentTimeMillis() - t);

        // Step 5: sales rep activity
        t = System.currentTimeMillis();
        List<SyntheticRepActivity> activities =
                salesRepActivityGenerator.generate(merchants, config);
        log.info("[Synthetic] 5/6 rep activities ({}) — {} ms",
                activities.size(), System.currentTimeMillis() - t);

        // Step 6: credit evaluations (depends on merchants + payments)
        t = System.currentTimeMillis();
        List<SyntheticCreditEvaluation> evaluations =
                creditHistoryGenerator.generate(merchants, payments, config);
        log.info("[Synthetic] 6/6 credit evaluations ({}) — {} ms",
                evaluations.size(), System.currentTimeMillis() - t);

        // Step 7: expiry batches (independent — no deps on other steps)
        t = System.currentTimeMillis();
        List<SyntheticExpiryBatch> expiryBatches = expiryBatchGenerator.generateBatches();
        log.info("[Synthetic] 7/9 expiry batches ({}) — {} ms",
                expiryBatches.size(), System.currentTimeMillis() - t);

        // Step 8: bank statement lines (depends on payments)
        t = System.currentTimeMillis();
        List<SyntheticBankStatementLine> bankStatementLines =
                bankStatementGenerator.generate(payments, config.randomSeed());
        log.info("[Synthetic] 8/9 bank statement lines ({}) — {} ms",
                bankStatementLines.size(), System.currentTimeMillis() - t);

        // Step 9: cash flow snapshots (depends on orders + payments)
        t = System.currentTimeMillis();
        List<SyntheticCashFlowSnapshot> cashFlowSnapshots =
                cashFlowGenerator.generate(orderResult.orders(), payments, config.randomSeed());
        log.info("[Synthetic] 9/9 cash flow snapshots ({}) — {} ms",
                cashFlowSnapshots.size(), System.currentTimeMillis() - t);

        SyntheticDataBundle bundle = SyntheticDataBundle.create(
                merchants,
                orderResult.orders(),
                orderResult.items(),
                payments,
                movements,
                activities,
                evaluations,
                expiryBatches,
                bankStatementLines,
                cashFlowSnapshots,
                config.randomSeed(),
                config);

        log.info("[Synthetic] Pipeline complete — total {} ms — record counts: {}",
                System.currentTimeMillis() - pipelineStart, bundle.getRecordCounts());

        return bundle;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Map<String, Object> buildArchetypeRatiosMap(SyntheticDataConfig config) {
        Map<String, Object> map = new HashMap<>();
        config.archetypeRatios().forEach((archetype, ratio) -> map.put(archetype.name(), ratio));
        return map;
    }

    private Map<String, Object> buildConfigSnapshot(SyntheticDataConfig config) {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("merchantCount",  config.merchantCount());
        snapshot.put("historyMonths",  config.historyMonths());
        snapshot.put("randomSeed",     config.randomSeed());
        snapshot.put("distributorId",  String.valueOf(config.distributorId()));
        buildArchetypeRatiosMap(config).forEach(
                (k, v) -> snapshot.put("archetype_" + k, v));
        return snapshot;
    }
}
