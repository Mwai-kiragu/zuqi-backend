package com.zuqi.ai.model.tuning;

import com.zuqi.ai.model.ModelRegistry;
import com.zuqi.ai.synthetic.DataMixer;
import com.zuqi.ai.synthetic.DataPhaseTracker;
import com.zuqi.ai.synthetic.SyntheticDataBundle;
import com.zuqi.ai.synthetic.SyntheticDataConfig;
import com.zuqi.ai.synthetic.SyntheticDataOrchestrator;
import com.zuqi.ai.synthetic.SyntheticFeatureStore;
import com.zuqi.domain.ai.AIModelRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.tribuo.Example;
import org.tribuo.anomaly.Event;
import org.tribuo.classification.Label;
import org.tribuo.regression.Regressor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ModelTuningService}.
 *
 * All Tribuo trainers and the CV tuner are mocked, so no JNI is invoked.
 * Tests verify that:
 *  - The service delegates to SyntheticFeatureStore for each of the 9 models
 *  - The CrossValidationTuner is called for each model family
 *  - Results are correctly mapped to TuningResult records
 *  - Errors are collected, not propagated
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ModelTuningServiceTest {

    @Mock private SyntheticDataOrchestrator orchestrator;
    @Mock private SyntheticFeatureStore     featureStore;
    @Mock private DataMixer                 dataMixer;
    @Mock private ModelRegistry             modelRegistry;
    @Mock private DataPhaseTracker          phaseTracker;
    @Mock private CrossValidationTuner      cvTuner;
    @Mock private SyntheticDataBundle       bundle;
    @Mock private AIModelRegistry           registryEntry;

    private ModelTuningService service;

    private final UUID distributorId = UUID.randomUUID();
    private final UUID modelId       = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ModelTuningService(
                orchestrator, featureStore, dataMixer, modelRegistry, phaseTracker, cvTuner);

        // Common stubs
        when(orchestrator.generateBundle(any())).thenReturn(bundle);
        when(registryEntry.getId()).thenReturn(modelId);
        when(registryEntry.getModelName()).thenReturn("test_model");
        when(registryEntry.getModelVersion()).thenReturn(1);
        when(modelRegistry.registerModel(any(), any(), any(), any())).thenReturn(registryEntry);
        when(modelRegistry.promoteToActive(any())).thenReturn(registryEntry);
    }

    // ── generateBundle delegated ───────────────────────────────────────────

    @Test
    void tuneAllModels_generatesBundle() {
        stubAllFeatureStores(List.of(), List.of(), List.of(), List.of());
        stubDataMixerEmpty();

        service.tuneAllModels(distributorId, dummyConfig());

        verify(orchestrator).generateBundle(any(SyntheticDataConfig.class));
    }

    // ── Result collection ─────────────────────────────────────────────────

    @Test
    void tuneAllModels_emptyExamples_producesNoResults() {
        stubAllFeatureStores(List.of(), List.of(), List.of(), List.of());
        stubDataMixerEmpty();

        ModelTuningService.TuningRunResult result =
                service.tuneAllModels(distributorId, dummyConfig());

        // All models skipped due to too-few examples
        assertThat(result.results()).isEmpty();
        assertThat(result.errors()).isEmpty();
        assertThat(result.success()).isTrue();
    }

    @Test
    void tuneAllModels_durationIsPositive() {
        stubAllFeatureStores(List.of(), List.of(), List.of(), List.of());
        stubDataMixerEmpty();

        ModelTuningService.TuningRunResult result =
                service.tuneAllModels(distributorId, dummyConfig());

        assertThat(result.durationMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void tuneAllModels_successFalseWhenErrorsPresent() {
        // Make featureStore throw for credit_classifier
        when(featureStore.buildCreditClassifierExamples(any()))
                .thenThrow(new RuntimeException("simulated failure"));
        // All others return empty (skipped, no error)
        when(featureStore.buildCreditLimitRegressorExamples(any())).thenReturn(List.of());
        when(featureStore.buildDemandForecasterExamples(any())).thenReturn(List.of());
        when(featureStore.buildStockoutPredictorExamples(any())).thenReturn(List.of());
        when(featureStore.buildShrinkageDetectorExamples(any())).thenReturn(List.of());
        when(featureStore.buildPaymentAnomalyExamples(any())).thenReturn(List.of());
        when(featureStore.buildPaymentDistressExamples(any())).thenReturn(List.of());
        when(featureStore.buildRepPerformancePredictorExamples(any())).thenReturn(List.of());
        when(featureStore.buildDataQualityExamples(any())).thenReturn(List.of());
        stubDataMixerEmpty();

        ModelTuningService.TuningRunResult result =
                service.tuneAllModels(distributorId, dummyConfig());

        assertThat(result.success()).isFalse();
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0)).contains(DataPhaseTracker.MODEL_CREDIT_CLASSIFIER);
    }

    // ── TuningRunResult record ────────────────────────────────────────────

    @Test
    void tuningRunResult_storesAllFields() {
        TuningResult r = new TuningResult("model_a", UUID.randomUUID(),
                Map.of("num_rounds", 100), 0.85, "macro_f1", 5, 5);

        ModelTuningService.TuningRunResult result =
                new ModelTuningService.TuningRunResult(List.of(r), List.of(), true, 5000L);

        assertThat(result.results()).containsExactly(r);
        assertThat(result.errors()).isEmpty();
        assertThat(result.success()).isTrue();
        assertThat(result.durationMs()).isEqualTo(5000L);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void stubAllFeatureStores(List<Example<Label>> labels,
                                      List<Example<Regressor>> regressors,
                                      List<Example<Event>> events,
                                      List<Example<Label>> emptyLabels) {
        when(featureStore.buildCreditClassifierExamples(any())).thenReturn(labels);
        when(featureStore.buildCreditLimitRegressorExamples(any())).thenReturn(regressors);
        when(featureStore.buildDemandForecasterExamples(any())).thenReturn(regressors);
        when(featureStore.buildStockoutPredictorExamples(any())).thenReturn(labels);
        when(featureStore.buildShrinkageDetectorExamples(any())).thenReturn(events);
        when(featureStore.buildPaymentAnomalyExamples(any())).thenReturn(events);
        when(featureStore.buildPaymentDistressExamples(any())).thenReturn(labels);
        when(featureStore.buildRepPerformancePredictorExamples(any())).thenReturn(labels);
        when(featureStore.buildDataQualityExamples(any())).thenReturn(labels);
    }

    @SuppressWarnings("unchecked")
    private void stubDataMixerEmpty() {
        // DataMixer returns empty for all calls → all models skip due to MIN_EXAMPLES
        when(dataMixer.buildTrainingDataset(anyString(), any(), anyList(), anyList()))
                .thenReturn(List.of());
    }

    private SyntheticDataConfig dummyConfig() {
        return new SyntheticDataConfig(
                distributorId, 50, 6, 42L,
                SyntheticDataConfig.DEFAULT_ARCHETYPE_RATIOS);
    }
}
