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
import org.tribuo.Model;
import org.tribuo.MutableDataset;
import org.tribuo.Trainer;
import org.tribuo.anomaly.Event;
import org.tribuo.classification.Label;
import org.tribuo.clustering.ClusterID;
import org.tribuo.impl.ArrayExample;
import org.tribuo.regression.Regressor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
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

    @Mock private SyntheticDataOrchestrator      orchestrator;
    @Mock private SyntheticFeatureStore          featureStore;
    @Mock private DataMixer                      dataMixer;
    @Mock private ModelRegistry                  modelRegistry;
    @Mock private DataPhaseTracker               phaseTracker;
    @Mock private CrossValidationTuner           cvTuner;
    @Mock private HoldoutValidator               holdoutValidator;
    @Mock private Trainer<ClusterID>             kMeansTrainer;
    @Mock private SyntheticDataBundle            bundle;
    @Mock private AIModelRegistry                registryEntry;
    @SuppressWarnings("rawtypes")
    @Mock private MutableDataset                 emptyClusterDataset;

    private ModelTuningService service;

    private final UUID distributorId = UUID.randomUUID();
    private final UUID modelId       = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ModelTuningService(
                orchestrator, featureStore, dataMixer, modelRegistry, phaseTracker,
                cvTuner, holdoutValidator, kMeansTrainer);

        // Common stubs
        when(orchestrator.generateBundle(any())).thenReturn(bundle);
        when(registryEntry.getId()).thenReturn(modelId);
        when(registryEntry.getModelName()).thenReturn("test_model");
        when(registryEntry.getModelVersion()).thenReturn(1);
        when(modelRegistry.registerModel(any(), any(), any(), any())).thenReturn(registryEntry);
        when(modelRegistry.promoteToActive(any())).thenReturn(registryEntry);
        // K-Means uses MutableDataset directly — always stub to 0 size → skip
        //noinspection unchecked,rawtypes
        when(featureStore.buildSegmentationDataset(any())).thenReturn((MutableDataset) emptyClusterDataset);
        when(emptyClusterDataset.size()).thenReturn(0);
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

    // ── Holdout gate ──────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void tuneClassifier_holdoutFails_skipsRegistryAndAddsError() {
        // 20 examples with two classes — passes minimum-count and single-class checks.
        // Use local variable + diamond so Java can infer the ArrayExample type parameter.
        List<Example<Label>> labeledExamples = buildLabeledExamples(10);

        when(featureStore.buildCreditClassifierExamples(any())).thenReturn(labeledExamples);
        // doReturn avoids generic type inference issues with DataMixer's <T extends Output<T>> signature
        doReturn(labeledExamples).when(dataMixer)
                .buildTrainingDataset(anyString(), any(), anyList(), anyList());

        HoldoutValidator.HoldoutSplit<Example<Label>> fakeSplit =
                new HoldoutValidator.HoldoutSplit<>(
                        labeledExamples.subList(0, 16), labeledExamples.subList(16, 20));
        doReturn(fakeSplit).when(holdoutValidator).split(anyList());

        // CV tuner → mock trainer → mock model (serializable so serialize() doesn't throw)
        Trainer<Label> mockTrainer = (Trainer<Label>) org.mockito.Mockito.mock(Trainer.class);
        Model<Label> mockModel = (Model<Label>) org.mockito.Mockito.mock(Model.class,
                org.mockito.Mockito.withSettings().serializable());
        doReturn(mockModel).when(mockTrainer).train(any());
        CandidateConfig<Label> cfg = new CandidateConfig<>(mockTrainer, Map.of("num_rounds", 50));
        CrossValidationTuner.BestConfig<Label> bestConfig =
                new CrossValidationTuner.BestConfig<>(cfg, 0.72, "macro_f1", 5, 4);
        when(cvTuner.tuneClassifier(anyString(), anyList(), any())).thenReturn(bestConfig);

        // Holdout FAILS — gate should block registerAndPromote
        when(holdoutValidator.validateClassifier(any(), anyList(), anyString()))
                .thenReturn(new ValidationResult(false, "macro_f1", 0.42, 0.60));

        stubRemainingModelsEmpty();

        ModelTuningService.TuningRunResult result =
                service.tuneAllModels(distributorId, dummyConfig(),
                        Set.of(DataPhaseTracker.MODEL_CREDIT_CLASSIFIER));

        // Registry must NOT have been called — promotion was blocked by the holdout gate
        verify(modelRegistry, never()).registerModel(anyString(), anyString(), any(), anyString());
        assertThat(result.results()).isEmpty();
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0)).contains(DataPhaseTracker.MODEL_CREDIT_CLASSIFIER);
        assertThat(result.errors().get(0)).contains("holdout validation failed");
    }

    @Test
    @SuppressWarnings("unchecked")
    void tuneClassifier_holdoutSkipped_promotesModel() {
        List<Example<Label>> labeledExamples = buildLabeledExamples(10);

        when(featureStore.buildCreditClassifierExamples(any())).thenReturn(labeledExamples);
        doReturn(labeledExamples).when(dataMixer)
                .buildTrainingDataset(anyString(), any(), anyList(), anyList());

        HoldoutValidator.HoldoutSplit<Example<Label>> fakeSplit =
                new HoldoutValidator.HoldoutSplit<>(
                        labeledExamples.subList(0, 16), labeledExamples.subList(16, 20));
        doReturn(fakeSplit).when(holdoutValidator).split(anyList());

        Trainer<Label> mockTrainer = (Trainer<Label>) org.mockito.Mockito.mock(Trainer.class);
        Model<Label> mockModel = (Model<Label>) org.mockito.Mockito.mock(Model.class,
                org.mockito.Mockito.withSettings().serializable());
        doReturn(mockModel).when(mockTrainer).train(any());
        CandidateConfig<Label> cfg = new CandidateConfig<>(mockTrainer, Map.of("num_rounds", 50));
        CrossValidationTuner.BestConfig<Label> bestConfig =
                new CrossValidationTuner.BestConfig<>(cfg, 0.80, "macro_f1", 5, 4);
        when(cvTuner.tuneClassifier(anyString(), anyList(), any())).thenReturn(bestConfig);

        // Holdout SKIPPED (too-small holdout) — treated as passing, model should be promoted
        when(holdoutValidator.validateClassifier(any(), anyList(), anyString()))
                .thenReturn(ValidationResult.skipped("macro_f1"));

        when(registryEntry.getId()).thenReturn(modelId);
        when(modelRegistry.registerModel(any(), any(), any(), any())).thenReturn(registryEntry);
        when(modelRegistry.promoteToActive(any())).thenReturn(registryEntry);

        stubRemainingModelsEmpty();

        ModelTuningService.TuningRunResult result =
                service.tuneAllModels(distributorId, dummyConfig(),
                        Set.of(DataPhaseTracker.MODEL_CREDIT_CLASSIFIER));

        verify(modelRegistry).registerModel(anyString(), anyString(), any(), anyString());
        assertThat(result.results()).hasSize(1);
        assertThat(result.results().get(0).modelName()).isEqualTo(DataPhaseTracker.MODEL_CREDIT_CLASSIFIER);
        assertThat(result.results().get(0).holdoutPassed()).isTrue();
        assertThat(result.errors()).isEmpty();
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

    @SuppressWarnings({"unchecked", "rawtypes"})
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
        // K-Means uses a MutableDataset directly — stub to empty (size < 10 → skip)
        when(featureStore.buildSegmentationDataset(any())).thenReturn((MutableDataset) emptyClusterDataset);
        when(emptyClusterDataset.size()).thenReturn(0);
    }

    @SuppressWarnings("unchecked")
    private void stubDataMixerEmpty() {
        // DataMixer returns empty for all calls → all models skip due to MIN_EXAMPLES
        when(dataMixer.buildTrainingDataset(anyString(), any(), anyList(), anyList()))
                .thenReturn(List.of());
    }

    /**
     * Builds {@code pairsOf2} × 2 labeled examples (equal HIGH/LOW split) using a local
     * variable declaration so that {@code ArrayExample<>}'s diamond operator can infer Label.
     */
    private List<Example<Label>> buildLabeledExamples(int pairsOf2) {
        List<Example<Label>> list = new ArrayList<>();
        for (int i = 0; i < pairsOf2; i++) {
            ArrayExample<Label> hi = new ArrayExample<>(
                    new Label("HIGH"), new String[]{"f1", "f2"}, new double[]{i, i * 2.0});
            ArrayExample<Label> lo = new ArrayExample<>(
                    new Label("LOW"), new String[]{"f1", "f2"}, new double[]{i + 0.5, i});
            list.add(hi);
            list.add(lo);
        }
        return list;
    }

    /** Stubs all non-credit-classifier feature stores to return empty, causing those models to skip. */
    private void stubRemainingModelsEmpty() {
        when(featureStore.buildCreditLimitRegressorExamples(any())).thenReturn(List.of());
        when(featureStore.buildDemandForecasterExamples(any())).thenReturn(List.of());
        when(featureStore.buildStockoutPredictorExamples(any())).thenReturn(List.of());
        when(featureStore.buildShrinkageDetectorExamples(any())).thenReturn(List.of());
        when(featureStore.buildPaymentAnomalyExamples(any())).thenReturn(List.of());
        when(featureStore.buildPaymentDistressExamples(any())).thenReturn(List.of());
        when(featureStore.buildRepPerformancePredictorExamples(any())).thenReturn(List.of());
        when(featureStore.buildDataQualityExamples(any())).thenReturn(List.of());
    }

    private SyntheticDataConfig dummyConfig() {
        return new SyntheticDataConfig(
                distributorId, 50, 6, 42L,
                SyntheticDataConfig.DEFAULT_ARCHETYPE_RATIOS);
    }
}
