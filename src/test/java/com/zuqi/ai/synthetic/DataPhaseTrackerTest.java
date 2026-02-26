package com.zuqi.ai.synthetic;

import com.zuqi.ai.event.DataPhaseTransitionEvent;
import com.zuqi.domain.ai.AIDataPhase;
import com.zuqi.domain.ai.DataPhase;
import com.zuqi.repository.AIDataPhaseRepository;
import com.zuqi.repository.DistributorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static com.zuqi.ai.synthetic.DataPhaseTracker.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataPhaseTrackerTest {

    @Mock private AIDataPhaseRepository   phaseRepository;
    @Mock private DistributorRepository   distributorRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Captor private ArgumentCaptor<DataPhaseTransitionEvent> eventCaptor;

    private DataPhaseTracker tracker;

    private static final String MODEL  = MODEL_CREDIT_CLASSIFIER;   // hybrid=200, real=500
    private static final UUID   DIST   = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        tracker = new DataPhaseTracker(phaseRepository, distributorRepository, eventPublisher);
        // Default: repository returns empty (no record yet)
        lenient().when(phaseRepository.findByModelNameAndDistributorId(any(), any()))
                .thenReturn(Optional.empty());
        // Default: save returns the entity unchanged
        lenient().when(phaseRepository.save(any(AIDataPhase.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(distributorRepository.findById(any()))
                .thenReturn(Optional.empty());
    }

    // ──────────────────────────────────────────────────────────────────────
    // getPhase
    // ──────────────────────────────────────────────────────────────────────

    @Test
    void getPhase_noRecord_returnsSynthetic() {
        assertThat(tracker.getPhase(MODEL, DIST)).isEqualTo(DataPhase.SYNTHETIC);
    }

    @Test
    void getPhase_existingRecord_returnsStoredPhase() {
        AIDataPhase existing = syntheticRecord(MODEL, DIST, DataPhase.HYBRID, 300, 700);
        when(phaseRepository.findByModelNameAndDistributorId(MODEL, DIST))
                .thenReturn(Optional.of(existing));

        assertThat(tracker.getPhase(MODEL, DIST)).isEqualTo(DataPhase.HYBRID);
    }

    // ──────────────────────────────────────────────────────────────────────
    // getRealDataRatio
    // ──────────────────────────────────────────────────────────────────────

    @Test
    void getRealDataRatio_noRecord_returnsZero() {
        assertThat(tracker.getRealDataRatio(MODEL, DIST)).isEqualTo(0.0);
    }

    @Test
    void getRealDataRatio_existingRecord_returnsStoredRatio() {
        // 300 real / (300+700) = 0.30
        AIDataPhase existing = syntheticRecord(MODEL, DIST, DataPhase.HYBRID, 300, 700);
        when(phaseRepository.findByModelNameAndDistributorId(MODEL, DIST))
                .thenReturn(Optional.of(existing));

        assertThat(tracker.getRealDataRatio(MODEL, DIST)).isEqualTo(0.30);
    }

    // ──────────────────────────────────────────────────────────────────────
    // updateCounts
    // ──────────────────────────────────────────────────────────────────────

    @Test
    void updateCounts_noExistingRecord_createsAndPersistsRecord() {
        tracker.updateCounts(MODEL, DIST, 100, 400);

        ArgumentCaptor<AIDataPhase> captor = ArgumentCaptor.forClass(AIDataPhase.class);
        verify(phaseRepository, atLeastOnce()).save(captor.capture());

        // The last save should have the updated counts
        AIDataPhase saved = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertThat(saved.getRealDataCount()).isEqualTo(100);
        assertThat(saved.getSyntheticDataCount()).isEqualTo(400);
    }

    @Test
    void updateCounts_isAdditive() {
        // Start with an existing record at 100 real / 400 synthetic
        AIDataPhase existing = syntheticRecord(MODEL, DIST, DataPhase.SYNTHETIC, 100, 400);
        when(phaseRepository.findByModelNameAndDistributorId(MODEL, DIST))
                .thenReturn(Optional.of(existing));

        tracker.updateCounts(MODEL, DIST, 50, 0);

        assertThat(existing.getRealDataCount()).isEqualTo(150);
        assertThat(existing.getSyntheticDataCount()).isEqualTo(400);
    }

    @Test
    void updateCounts_recalculatesRatio() {
        AIDataPhase existing = syntheticRecord(MODEL, DIST, DataPhase.SYNTHETIC, 0, 500);
        when(phaseRepository.findByModelNameAndDistributorId(MODEL, DIST))
                .thenReturn(Optional.of(existing));

        tracker.updateCounts(MODEL, DIST, 100, 0);   // 100 real / 600 total = 0.1666...

        double ratio = existing.getRealDataRatio();
        assertThat(ratio).isBetween(0.166, 0.167);
    }

    // ──────────────────────────────────────────────────────────────────────
    // evaluatePhase — SYNTHETIC stays
    // ──────────────────────────────────────────────────────────────────────

    @Test
    void evaluatePhase_belowHybridThreshold_staysSynthetic() {
        // credit_classifier hybrid threshold = 200; only 100 real
        AIDataPhase existing = syntheticRecord(MODEL, DIST, DataPhase.SYNTHETIC, 100, 900);
        when(phaseRepository.findByModelNameAndDistributorId(MODEL, DIST))
                .thenReturn(Optional.of(existing));

        DataPhase result = tracker.evaluatePhase(MODEL, DIST);

        assertThat(result).isEqualTo(DataPhase.SYNTHETIC);
        verifyNoInteractions(eventPublisher);
    }

    // ──────────────────────────────────────────────────────────────────────
    // evaluatePhase — SYNTHETIC → HYBRID
    // ──────────────────────────────────────────────────────────────────────

    @Test
    void evaluatePhase_meetsHybridThreshold_transitionsToHybrid() {
        // credit_classifier hybrid = 200 real
        AIDataPhase existing = syntheticRecord(MODEL, DIST, DataPhase.SYNTHETIC, 200, 800);
        when(phaseRepository.findByModelNameAndDistributorId(MODEL, DIST))
                .thenReturn(Optional.of(existing));

        DataPhase result = tracker.evaluatePhase(MODEL, DIST);

        assertThat(result).isEqualTo(DataPhase.HYBRID);
        assertThat(existing.getCurrentPhase()).isEqualTo(DataPhase.HYBRID);
        assertThat(existing.getTransitionedAt()).isNotNull();
    }

    @Test
    void evaluatePhase_hybridTransition_publishesEvent() {
        AIDataPhase existing = syntheticRecord(MODEL, DIST, DataPhase.SYNTHETIC, 250, 750);
        when(phaseRepository.findByModelNameAndDistributorId(MODEL, DIST))
                .thenReturn(Optional.of(existing));

        tracker.evaluatePhase(MODEL, DIST);

        verify(eventPublisher).publishEvent(eventCaptor.capture());
        DataPhaseTransitionEvent event = eventCaptor.getValue();
        assertThat(event.getModelName()).isEqualTo(MODEL);
        assertThat(event.getDistributorId()).isEqualTo(DIST);
        assertThat(event.getFromPhase()).isEqualTo(DataPhase.SYNTHETIC);
        assertThat(event.getToPhase()).isEqualTo(DataPhase.HYBRID);
        assertThat(event.getRealDataCount()).isEqualTo(250);
    }

    // ──────────────────────────────────────────────────────────────────────
    // evaluatePhase — HYBRID stays
    // ──────────────────────────────────────────────────────────────────────

    @Test
    void evaluatePhase_hybridCountMetButRatioBelowThreshold_staysHybrid() {
        // real=500 (≥ realMinRealCount=500) but ratio = 500/5500 = 0.09 < 0.80
        AIDataPhase existing = syntheticRecord(MODEL, DIST, DataPhase.HYBRID, 500, 5000);
        when(phaseRepository.findByModelNameAndDistributorId(MODEL, DIST))
                .thenReturn(Optional.of(existing));

        DataPhase result = tracker.evaluatePhase(MODEL, DIST);

        assertThat(result).isEqualTo(DataPhase.HYBRID);
        verifyNoInteractions(eventPublisher);
    }

    // ──────────────────────────────────────────────────────────────────────
    // evaluatePhase — HYBRID → REAL
    // ──────────────────────────────────────────────────────────────────────

    @Test
    void evaluatePhase_meetsRealThresholds_transitionsToReal() {
        // credit_classifier real = 500 + ratio ≥ 0.80
        // 500 real / 600 total = 0.833...
        AIDataPhase existing = syntheticRecord(MODEL, DIST, DataPhase.HYBRID, 500, 100);
        when(phaseRepository.findByModelNameAndDistributorId(MODEL, DIST))
                .thenReturn(Optional.of(existing));

        DataPhase result = tracker.evaluatePhase(MODEL, DIST);

        assertThat(result).isEqualTo(DataPhase.REAL);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getFromPhase()).isEqualTo(DataPhase.HYBRID);
        assertThat(eventCaptor.getValue().getToPhase()).isEqualTo(DataPhase.REAL);
    }

    // ──────────────────────────────────────────────────────────────────────
    // evaluatePhase — REAL stays
    // ──────────────────────────────────────────────────────────────────────

    @Test
    void evaluatePhase_alreadyReal_staysReal() {
        AIDataPhase existing = syntheticRecord(MODEL, DIST, DataPhase.REAL, 1000, 50);
        when(phaseRepository.findByModelNameAndDistributorId(MODEL, DIST))
                .thenReturn(Optional.of(existing));

        DataPhase result = tracker.evaluatePhase(MODEL, DIST);

        assertThat(result).isEqualTo(DataPhase.REAL);
        verifyNoInteractions(eventPublisher);
    }

    // ──────────────────────────────────────────────────────────────────────
    // evaluatePhase — lastEvaluatedAt is always updated
    // ──────────────────────────────────────────────────────────────────────

    @Test
    void evaluatePhase_alwaysUpdatesLastEvaluatedAt() {
        AIDataPhase existing = syntheticRecord(MODEL, DIST, DataPhase.SYNTHETIC, 10, 500);
        when(phaseRepository.findByModelNameAndDistributorId(MODEL, DIST))
                .thenReturn(Optional.of(existing));

        tracker.evaluatePhase(MODEL, DIST);

        assertThat(existing.getLastEvaluatedAt()).isNotNull();
    }

    // ──────────────────────────────────────────────────────────────────────
    // evaluatePhase — no event when no change
    // ──────────────────────────────────────────────────────────────────────

    @Test
    void evaluatePhase_noChange_noEventPublished() {
        AIDataPhase existing = syntheticRecord(MODEL, DIST, DataPhase.SYNTHETIC, 10, 500);
        when(phaseRepository.findByModelNameAndDistributorId(MODEL, DIST))
                .thenReturn(Optional.of(existing));

        tracker.evaluatePhase(MODEL, DIST);

        verifyNoInteractions(eventPublisher);
    }

    // ──────────────────────────────────────────────────────────────────────
    // getThreshold — all 9 models defined
    // ──────────────────────────────────────────────────────────────────────

    @Test
    void getThreshold_allNineModelsAreDefined() {
        String[] models = {
            MODEL_DEMAND_FORECASTER, MODEL_CREDIT_CLASSIFIER, MODEL_STOCKOUT_PREDICTOR,
            MODEL_SHRINKAGE_DETECTOR, MODEL_PAYMENT_ANOMALY_DETECTOR,
            MODEL_DATA_QUALITY_DETECTOR, MODEL_REP_PERFORMANCE_PREDICTOR,
            MODEL_CREDIT_LIMIT_REGRESSOR, MODEL_PAYMENT_DISTRESS_CLASSIFIER
        };
        for (String model : models) {
            TransitionThreshold t = tracker.getThreshold(model);
            assertThat(t.modelName())
                    .as("threshold for " + model + " should not use default name")
                    .isEqualTo(model);
        }
    }

    @Test
    void getThreshold_unknownModel_returnsDefault() {
        TransitionThreshold t = tracker.getThreshold("some_unknown_model");
        assertThat(t).isEqualTo(TransitionThreshold.DEFAULT_THRESHOLD);
    }

    @Test
    void getThreshold_creditClassifier_hasCorrectValues() {
        TransitionThreshold t = tracker.getThreshold(MODEL_CREDIT_CLASSIFIER);
        assertThat(t.hybridMinRealCount()).isEqualTo(200);
        assertThat(t.realMinRealCount()).isEqualTo(500);
        assertThat(t.realMinRatio()).isEqualTo(0.80);
    }

    @Test
    void getThreshold_demandForecaster_hasCorrectValues() {
        TransitionThreshold t = tracker.getThreshold(MODEL_DEMAND_FORECASTER);
        assertThat(t.hybridMinRealCount()).isEqualTo(50);
        assertThat(t.realMinRealCount()).isEqualTo(200);
    }

    // ──────────────────────────────────────────────────────────────────────
    // updateCounts + evaluatePhase integration
    // ──────────────────────────────────────────────────────────────────────

    @Test
    void updateCounts_thenEvaluate_transitionsToHybrid() {
        // demand_forecaster: hybrid = 50 real
        String demandModel = MODEL_DEMAND_FORECASTER;
        AIDataPhase phase = syntheticRecord(demandModel, DIST, DataPhase.SYNTHETIC, 0, 500);
        when(phaseRepository.findByModelNameAndDistributorId(demandModel, DIST))
                .thenReturn(Optional.of(phase));

        // Add 60 real examples — crosses hybrid threshold of 50
        tracker.updateCounts(demandModel, DIST, 60, 0);
        DataPhase result = tracker.evaluatePhase(demandModel, DIST);

        assertThat(result).isEqualTo(DataPhase.HYBRID);
    }

    // ──────────────────────────────────────────────────────────────────────
    // helpers
    // ──────────────────────────────────────────────────────────────────────

    /** Build an in-memory AIDataPhase record (no JPA lifecycle hooks). */
    private AIDataPhase syntheticRecord(String modelName, UUID distributorId,
                                        DataPhase phase, int real, int synthetic) {
        int total    = real + synthetic;
        double ratio = total > 0 ? (double) real / total : 0.0;
        return AIDataPhase.builder()
                .id(UUID.randomUUID())
                .modelName(modelName)
                .currentPhase(phase)
                .realDataCount(real)
                .syntheticDataCount(synthetic)
                .realDataRatio(ratio)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
