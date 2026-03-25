package com.zuqi.ai.synthetic;

import com.zuqi.ai.synthetic.dto.*;

import com.zuqi.ai.synthetic.generators.BusinessNameGenerator;
import com.zuqi.ai.synthetic.generators.CreditHistoryGenerator;
import com.zuqi.ai.synthetic.generators.InventoryMovementGenerator;
import com.zuqi.ai.synthetic.generators.MerchantProfileGenerator;
import com.zuqi.ai.synthetic.generators.OrderHistoryGenerator;
import com.zuqi.ai.synthetic.generators.PaymentBehaviorGenerator;
import com.zuqi.ai.synthetic.generators.SalesRepActivityGenerator;
import com.zuqi.ai.synthetic.generators.SyntheticBankStatementGenerator;
import com.zuqi.ai.synthetic.generators.SyntheticCashFlowGenerator;
import com.zuqi.ai.synthetic.generators.SyntheticExpiryBatchGenerator;
import com.zuqi.domain.ai.AISyntheticRun;
import com.zuqi.domain.ai.SyntheticRunStatus;
import com.zuqi.domain.ai.SyntheticRunType;
import com.zuqi.repository.AISyntheticRunRepository;
import com.zuqi.repository.DistributorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link SyntheticDataOrchestrator}.
 *
 * <p>Generators are exercised end-to-end with a real (not mocked) chain using
 * a small config (10 merchants, 3 months). Only the database-layer beans
 * ({@link AISyntheticRunRepository}, {@link DistributorRepository}) are mocked.
 *
 * <p>The {@link BusinessNameGenerator} is mocked to return simple template names
 * without calling Ollama.
 */
@ExtendWith(MockitoExtension.class)
class SyntheticDataOrchestratorTest {

    // ── mocked infrastructure ──────────────────────────────────────────────

    @Mock private BusinessNameGenerator    nameGenerator;
    @Mock private AISyntheticRunRepository runRepository;
    @Mock private DistributorRepository    distributorRepository;

    // ── system under test ──────────────────────────────────────────────────

    private SyntheticDataOrchestrator orchestrator;

    // ── shared config ──────────────────────────────────────────────────────

    private static final long SEED = 42L;
    private static final SyntheticDataConfig SMALL_CONFIG = new SyntheticDataConfig(
            null, 10, 3, SEED, SyntheticDataConfig.DEFAULT_ARCHETYPE_RATIOS);

    // ──────────────────────────────────────────────────────────────────────
    // Setup
    // ──────────────────────────────────────────────────────────────────────

    @BeforeEach
    void setUp() {
        // Return template names for all three categories so MerchantProfileGenerator works
        lenient().when(nameGenerator.generateBatch(anyString(), anyInt(), anyLong()))
                .thenAnswer(inv -> {
                    int count = inv.getArgument(1);
                    String cat = inv.getArgument(0);
                    List<String> names = new java.util.ArrayList<>();
                    for (int i = 0; i < count; i++) names.add(cat + "_biz_" + i);
                    return names;
                });

        // Repository: save returns the entity with a new UUID (simulate DB auto-ID)
        lenient().when(runRepository.save(any(AISyntheticRun.class))).thenAnswer(inv -> {
            AISyntheticRun run = inv.getArgument(0);
            if (run.getId() == null) {
                // simulate @GeneratedValue by setting an id reflectively via builder
                return AISyntheticRun.builder()
                        .id(UUID.randomUUID())
                        .distributor(run.getDistributor())
                        .runType(run.getRunType())
                        .randomSeed(run.getRandomSeed())
                        .merchantCount(run.getMerchantCount())
                        .historyMonths(run.getHistoryMonths())
                        .archetypeRatios(run.getArchetypeRatios())
                        .configSnapshot(run.getConfigSnapshot())
                        .triggeredBy(run.getTriggeredBy())
                        .status(run.getStatus())
                        .startedAt(run.getStartedAt())
                        .build();
            }
            return run;
        });

        lenient().when(distributorRepository.findById(any()))
                .thenReturn(Optional.empty());

        MerchantProfileGenerator      merchantGen      = new MerchantProfileGenerator(nameGenerator);
        OrderHistoryGenerator          orderGen         = new OrderHistoryGenerator();
        PaymentBehaviorGenerator       paymentGen       = new PaymentBehaviorGenerator();
        InventoryMovementGenerator     inventoryGen     = new InventoryMovementGenerator();
        SalesRepActivityGenerator      repGen           = new SalesRepActivityGenerator();
        CreditHistoryGenerator         creditGen        = new CreditHistoryGenerator();
        SyntheticExpiryBatchGenerator  expiryGen        = new SyntheticExpiryBatchGenerator();
        SyntheticBankStatementGenerator bankStmtGen     = new SyntheticBankStatementGenerator();
        SyntheticCashFlowGenerator     cashFlowGen      = new SyntheticCashFlowGenerator();

        orchestrator = new SyntheticDataOrchestrator(
                merchantGen, orderGen, paymentGen, inventoryGen, repGen, creditGen,
                expiryGen, bankStmtGen, cashFlowGen, runRepository, distributorRepository);
    }

    // ──────────────────────────────────────────────────────────────────────
    // generateBundle — core pipeline
    // ──────────────────────────────────────────────────────────────────────

    @Test
    void generateBundle_shouldReturnNonNullBundle() {
        SyntheticDataBundle bundle = orchestrator.generateBundle(SMALL_CONFIG);
        assertThat(bundle).isNotNull();
    }

    @Test
    void generateBundle_shouldProduceCorrectMerchantCount() {
        SyntheticDataBundle bundle = orchestrator.generateBundle(SMALL_CONFIG);
        assertThat(bundle.getMerchants()).hasSize(10);
    }

    @Test
    void generateBundle_shouldPopulateAllSevenLists() {
        SyntheticDataBundle bundle = orchestrator.generateBundle(SMALL_CONFIG);
        assertThat(bundle.getMerchants()).isNotEmpty();
        assertThat(bundle.getOrders()).isNotEmpty();
        assertThat(bundle.getOrderItems()).isNotEmpty();
        assertThat(bundle.getPayments()).isNotEmpty();
        assertThat(bundle.getInventoryMovements()).isNotEmpty();
        assertThat(bundle.getRepActivities()).isNotEmpty();
        assertThat(bundle.getCreditEvaluations()).isNotEmpty();
    }

    @Test
    void generateBundle_shouldBeDeterministic() {
        SyntheticDataBundle b1 = orchestrator.generateBundle(SMALL_CONFIG);
        SyntheticDataBundle b2 = orchestrator.generateBundle(SMALL_CONFIG);

        assertThat(b1.getMerchants()).hasSize(b2.getMerchants().size());
        assertThat(b1.getOrders()).hasSize(b2.getOrders().size());
        assertThat(b1.getPayments()).hasSize(b2.getPayments().size());
        assertThat(b1.getCreditEvaluations()).hasSize(b2.getCreditEvaluations().size());
    }

    @Test
    void generateBundle_recordCountsShouldMatchListSizes() {
        SyntheticDataBundle bundle = orchestrator.generateBundle(SMALL_CONFIG);
        var counts = bundle.getRecordCounts();

        assertThat(counts.get("merchants")).isEqualTo(bundle.getMerchants().size());
        assertThat(counts.get("orders")).isEqualTo(bundle.getOrders().size());
        assertThat(counts.get("orderItems")).isEqualTo(bundle.getOrderItems().size());
        assertThat(counts.get("payments")).isEqualTo(bundle.getPayments().size());
        assertThat(counts.get("inventoryMovements")).isEqualTo(bundle.getInventoryMovements().size());
        assertThat(counts.get("repActivities")).isEqualTo(bundle.getRepActivities().size());
        assertThat(counts.get("creditEvaluations")).isEqualTo(bundle.getCreditEvaluations().size());
    }

    @Test
    void generateBundle_crossReferenceMapsAreSane() {
        SyntheticDataBundle bundle = orchestrator.generateBundle(SMALL_CONFIG);

        // Every merchant should have at least some orders
        long merchantsWithOrders = bundle.getMerchants().stream()
                .filter(m -> !bundle.getOrdersForMerchant(m.syntheticId()).isEmpty())
                .count();
        assertThat(merchantsWithOrders).isPositive();

        // Every order item must reference a known order
        var orderIds = bundle.getOrders().stream()
                .map(o -> o.syntheticId())
                .collect(java.util.stream.Collectors.toSet());
        boolean allItemsHaveKnownOrder = bundle.getOrderItems().stream()
                .allMatch(item -> orderIds.contains(item.orderRef()));
        assertThat(allItemsHaveKnownOrder).isTrue();
    }

    @Test
    void generateBundle_configIsStoredOnBundle() {
        SyntheticDataBundle bundle = orchestrator.generateBundle(SMALL_CONFIG);
        assertThat(bundle.getConfig()).isEqualTo(SMALL_CONFIG);
        assertThat(bundle.getGenerationSeed()).isEqualTo(SEED);
    }

    // ──────────────────────────────────────────────────────────────────────
    // createRunRecord
    // ──────────────────────────────────────────────────────────────────────

    @Test
    void createRunRecord_shouldPersistRunningRecord() {
        AISyntheticRun run = orchestrator.createRunRecord(null, SMALL_CONFIG, "test-user");

        ArgumentCaptor<AISyntheticRun> captor = ArgumentCaptor.forClass(AISyntheticRun.class);
        verify(runRepository).save(captor.capture());

        AISyntheticRun saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(SyntheticRunStatus.RUNNING);
        assertThat(saved.getRunType()).isEqualTo(SyntheticRunType.FULL_SEED);
        assertThat(saved.getTriggeredBy()).isEqualTo("test-user");
        assertThat(saved.getMerchantCount()).isEqualTo(10);
        assertThat(saved.getHistoryMonths()).isEqualTo(3);
        assertThat(saved.getRandomSeed()).isEqualTo(SEED);
    }

    @Test
    void createRunRecord_archetypeRatiosShouldContainAllArchetypes() {
        orchestrator.createRunRecord(null, SMALL_CONFIG, "test");

        ArgumentCaptor<AISyntheticRun> captor = ArgumentCaptor.forClass(AISyntheticRun.class);
        verify(runRepository).save(captor.capture());

        var ratios = captor.getValue().getArchetypeRatios();
        assertThat(ratios).isNotNull().isNotEmpty();
        assertThat(ratios).containsKey("STEADY_GROWER");
        assertThat(ratios).containsKey("DEFAULTER");
    }

    @Test
    void createRunRecord_configSnapshotShouldContainKeyFields() {
        orchestrator.createRunRecord(null, SMALL_CONFIG, "test");

        ArgumentCaptor<AISyntheticRun> captor = ArgumentCaptor.forClass(AISyntheticRun.class);
        verify(runRepository).save(captor.capture());

        var snapshot = captor.getValue().getConfigSnapshot();
        assertThat(snapshot).containsKey("merchantCount");
        assertThat(snapshot).containsKey("historyMonths");
        assertThat(snapshot).containsKey("randomSeed");
    }

    @Test
    void createRunRecord_withDistributorId_shouldQueryDistributorRepository() {
        UUID distributorId = UUID.randomUUID();
        when(distributorRepository.findById(distributorId)).thenReturn(Optional.empty());

        orchestrator.createRunRecord(distributorId, SMALL_CONFIG, "admin");

        verify(distributorRepository).findById(distributorId);
    }

    // ──────────────────────────────────────────────────────────────────────
    // completeRun
    // ──────────────────────────────────────────────────────────────────────

    @Test
    void completeRun_shouldUpdateStatusAndRecordCounts() {
        UUID runId = UUID.randomUUID();
        AISyntheticRun run = AISyntheticRun.builder()
                .id(runId)
                .status(SyntheticRunStatus.RUNNING)
                .runType(SyntheticRunType.FULL_SEED)
                .startedAt(java.time.LocalDateTime.now())
                .build();
        when(runRepository.findById(runId)).thenReturn(Optional.of(run));
        when(runRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SyntheticDataBundle bundle = orchestrator.generateBundle(SMALL_CONFIG);
        orchestrator.completeRun(runId, bundle, 5_000L);

        assertThat(run.getStatus()).isEqualTo(SyntheticRunStatus.COMPLETED);
        assertThat(run.getDurationMs()).isEqualTo(5_000L);
        assertThat(run.getCompletedAt()).isNotNull();
        assertThat(run.getRecordsGenerated()).isNotNull();
        assertThat(run.getRecordsGenerated()).containsKey("merchants");
    }

    // ──────────────────────────────────────────────────────────────────────
    // failRun
    // ──────────────────────────────────────────────────────────────────────

    @Test
    void failRun_shouldUpdateStatusAndErrorMessage() {
        UUID runId = UUID.randomUUID();
        AISyntheticRun run = AISyntheticRun.builder()
                .id(runId)
                .status(SyntheticRunStatus.RUNNING)
                .runType(SyntheticRunType.FULL_SEED)
                .startedAt(java.time.LocalDateTime.now())
                .build();
        when(runRepository.findById(runId)).thenReturn(Optional.of(run));
        when(runRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        orchestrator.failRun(runId, "Out of memory", 1_200L);

        assertThat(run.getStatus()).isEqualTo(SyntheticRunStatus.FAILED);
        assertThat(run.getErrorMessage()).isEqualTo("Out of memory");
        assertThat(run.getDurationMs()).isEqualTo(1_200L);
        assertThat(run.getCompletedAt()).isNotNull();
    }

    // ──────────────────────────────────────────────────────────────────────
    // getRunStatus
    // ──────────────────────────────────────────────────────────────────────

    @Test
    void getRunStatus_shouldDelegateToRepository() {
        UUID runId = UUID.randomUUID();
        AISyntheticRun run = AISyntheticRun.builder()
                .id(runId)
                .status(SyntheticRunStatus.RUNNING)
                .runType(SyntheticRunType.FULL_SEED)
                .startedAt(java.time.LocalDateTime.now())
                .build();
        when(runRepository.findById(runId)).thenReturn(Optional.of(run));

        Optional<AISyntheticRun> result = orchestrator.getRunStatus(runId);
        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(runId);
    }

    @Test
    void getRunStatus_unknownId_shouldReturnEmpty() {
        UUID runId = UUID.randomUUID();
        when(runRepository.findById(runId)).thenReturn(Optional.empty());

        Optional<AISyntheticRun> result = orchestrator.getRunStatus(runId);
        assertThat(result).isEmpty();
    }
}
