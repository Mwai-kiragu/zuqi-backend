package com.zuqi.ai.synthetic;

import com.zuqi.ai.model.ModelRegistry;
import com.zuqi.domain.ai.AIModelRegistry;
import com.zuqi.domain.ai.DataPhase;
import com.zuqi.domain.ai.ModelStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tribuo.Example;
import org.tribuo.Model;
import org.tribuo.Trainer;
import org.tribuo.anomaly.Event;
import org.tribuo.classification.Label;
import org.tribuo.impl.ArrayExample;
import org.tribuo.regression.Regressor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link SyntheticModelTrainer}.
 *
 * <p>All dependencies (including Tribuo {@link Trainer}s) are mocked so:
 * <ul>
 *   <li>No XGBoost JNI or LibSVM native calls occur</li>
 *   <li>No Spring context or database is required</li>
 *   <li>Tests focus on the orchestration logic (wiring, thresholds, errors)</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class SyntheticModelTrainerTest {

    @Mock SyntheticFeatureStore  featureStore;
    @Mock DataMixer              dataMixer;
    @Mock ModelRegistry          modelRegistry;
    @Mock DataPhaseTracker       phaseTracker;

    @SuppressWarnings("unchecked")
    @Mock Trainer<Label>    classificationTrainer;
    @SuppressWarnings("unchecked")
    @Mock Trainer<Regressor> regressionTrainer;
    @SuppressWarnings("unchecked")
    @Mock Trainer<Event>    anomalyTrainer;

    private SyntheticModelTrainer trainer;

    private static final UUID DISTRIBUTOR_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        trainer = new SyntheticModelTrainer(
                featureStore, dataMixer, modelRegistry, phaseTracker,
                classificationTrainer, regressionTrainer, anomalyTrainer);
    }

    // ── Registry interactions ────────────────────────────────────────────────

    @Test
    void allNineModels_registeredAndPromoted() {
        stubAllModels();

        trainer.trainAllModels(emptyBundle(), DISTRIBUTOR_ID);

        // Each model: registerModel → updateModelAfterTraining → setDataPhaseMetadata → promoteToActive
        verify(modelRegistry, times(9)).registerModel(any(), any(), any(), any());
        verify(modelRegistry, times(9)).updateModelAfterTraining(any(), any(), any(), any());
        verify(modelRegistry, times(9)).setDataPhaseMetadata(any(), eq(DataPhase.SYNTHETIC), anyInt(), eq(0));
        verify(modelRegistry, times(9)).promoteToActive(any());
    }

    @Test
    void allNineModels_phaseTrackerUpdated() {
        stubAllModels();

        trainer.trainAllModels(emptyBundle(), DISTRIBUTOR_ID);

        verify(phaseTracker, times(9)).updateCounts(any(), eq(DISTRIBUTOR_ID), eq(0), anyInt());
        verify(phaseTracker, times(9)).evaluatePhase(any(), eq(DISTRIBUTOR_ID));
    }

    @Test
    void allNineModels_correctModelNamesPassedToRegistry() {
        stubAllModels();

        trainer.trainAllModels(emptyBundle(), DISTRIBUTOR_ID);

        verify(modelRegistry).registerModel(eq(DataPhaseTracker.MODEL_CREDIT_CLASSIFIER),          any(), any(), any());
        verify(modelRegistry).registerModel(eq(DataPhaseTracker.MODEL_CREDIT_LIMIT_REGRESSOR),     any(), any(), any());
        verify(modelRegistry).registerModel(eq(DataPhaseTracker.MODEL_DEMAND_FORECASTER),          any(), any(), any());
        verify(modelRegistry).registerModel(eq(DataPhaseTracker.MODEL_STOCKOUT_PREDICTOR),         any(), any(), any());
        verify(modelRegistry).registerModel(eq(DataPhaseTracker.MODEL_SHRINKAGE_DETECTOR),         any(), any(), any());
        verify(modelRegistry).registerModel(eq(DataPhaseTracker.MODEL_PAYMENT_ANOMALY_DETECTOR),   any(), any(), any());
        verify(modelRegistry).registerModel(eq(DataPhaseTracker.MODEL_PAYMENT_DISTRESS_CLASSIFIER),any(), any(), any());
        verify(modelRegistry).registerModel(eq(DataPhaseTracker.MODEL_REP_PERFORMANCE_PREDICTOR),  any(), any(), any());
        verify(modelRegistry).registerModel(eq(DataPhaseTracker.MODEL_DATA_QUALITY_DETECTOR),      any(), any(), any());
    }

    // ── Result structure ─────────────────────────────────────────────────────

    @Test
    void result_successTrue_whenAllModelsTrainedWithoutError() {
        stubAllModels();

        SyntheticModelTrainer.SyntheticTrainingResult result =
                trainer.trainAllModels(emptyBundle(), DISTRIBUTOR_ID);

        assertThat(result.success()).isTrue();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void result_trainedModelIds_containsAllNineEntries() {
        stubAllModels();

        SyntheticModelTrainer.SyntheticTrainingResult result =
                trainer.trainAllModels(emptyBundle(), DISTRIBUTOR_ID);

        assertThat(result.trainedModelIds()).containsKeys(
                DataPhaseTracker.MODEL_CREDIT_CLASSIFIER,
                DataPhaseTracker.MODEL_CREDIT_LIMIT_REGRESSOR,
                DataPhaseTracker.MODEL_DEMAND_FORECASTER,
                DataPhaseTracker.MODEL_STOCKOUT_PREDICTOR,
                DataPhaseTracker.MODEL_SHRINKAGE_DETECTOR,
                DataPhaseTracker.MODEL_PAYMENT_ANOMALY_DETECTOR,
                DataPhaseTracker.MODEL_PAYMENT_DISTRESS_CLASSIFIER,
                DataPhaseTracker.MODEL_REP_PERFORMANCE_PREDICTOR,
                DataPhaseTracker.MODEL_DATA_QUALITY_DETECTOR);
    }

    @Test
    void result_exampleCounts_reportedPerModel() {
        stubClassificationExamples(15);
        stubRegressionExamples(12);
        stubDemandForecasterExamples(18);
        stubStockoutExamples(11);
        stubShrinkageExamples(8);
        stubPaymentAnomalyExamples(9);
        stubPaymentDistressExamples(14);
        stubRepPerformanceExamples(10);
        stubDataQualityExamples(13);

        SyntheticModelTrainer.SyntheticTrainingResult result =
                trainer.trainAllModels(emptyBundle(), DISTRIBUTOR_ID);

        assertThat(result.exampleCounts())
                .containsEntry(DataPhaseTracker.MODEL_CREDIT_CLASSIFIER,          15)
                .containsEntry(DataPhaseTracker.MODEL_CREDIT_LIMIT_REGRESSOR,     12)
                .containsEntry(DataPhaseTracker.MODEL_DEMAND_FORECASTER,          18)
                .containsEntry(DataPhaseTracker.MODEL_STOCKOUT_PREDICTOR,         11)
                .containsEntry(DataPhaseTracker.MODEL_SHRINKAGE_DETECTOR,          8)
                .containsEntry(DataPhaseTracker.MODEL_PAYMENT_ANOMALY_DETECTOR,    9)
                .containsEntry(DataPhaseTracker.MODEL_PAYMENT_DISTRESS_CLASSIFIER,14)
                .containsEntry(DataPhaseTracker.MODEL_REP_PERFORMANCE_PREDICTOR,  10)
                .containsEntry(DataPhaseTracker.MODEL_DATA_QUALITY_DETECTOR,      13);
    }

    @Test
    void result_durationMs_greaterThanOrEqualToZero() {
        stubAllModels();

        SyntheticModelTrainer.SyntheticTrainingResult result =
                trainer.trainAllModels(emptyBundle(), DISTRIBUTOR_ID);

        assertThat(result.durationMs()).isGreaterThanOrEqualTo(0);
    }

    // ── Skip / threshold logic ───────────────────────────────────────────────

    @Test
    void tooFewClassificationExamples_skipsWithoutCrash() {
        // Supply fewer than MIN (10) — only 3 examples for credit_classifier
        lenient().when(featureStore.buildCreditClassifierExamples(any()))
                .thenReturn(buildClassificationList(3));
        lenient().when(dataMixer.buildTrainingDataset(
                        eq(DataPhaseTracker.MODEL_CREDIT_CLASSIFIER), any(), any(), any()))
                .thenAnswer(inv -> inv.<List<?>>getArgument(3));

        stubRegressionExamples(12);
        stubDemandForecasterExamples(18);
        stubStockoutExamples(11);
        stubShrinkageExamples(8);
        stubPaymentAnomalyExamples(8);
        stubPaymentDistressExamples(14);
        stubRepPerformanceExamples(10);
        stubDataQualityExamples(13);

        SyntheticModelTrainer.SyntheticTrainingResult result =
                trainer.trainAllModels(emptyBundle(), DISTRIBUTOR_ID);

        // credit_classifier skipped → only 8 models registered
        verify(modelRegistry, times(8)).registerModel(any(), any(), any(), any());
        assertThat(result.trainedModelIds())
                .doesNotContainKey(DataPhaseTracker.MODEL_CREDIT_CLASSIFIER);
        assertThat(result.success()).isTrue();   // skip is not an error
    }

    @Test
    void singleClassDataset_skipsClassifierWithoutError() {
        // 11 examples, ALL NO_DEFAULT — single-class dataset
        lenient().when(featureStore.buildCreditClassifierExamples(any()))
                .thenReturn(buildSingleClassList(11));
        lenient().when(dataMixer.buildTrainingDataset(
                        eq(DataPhaseTracker.MODEL_CREDIT_CLASSIFIER), any(), any(), any()))
                .thenAnswer(inv -> inv.<List<?>>getArgument(3));

        stubRegressionExamples(12);
        stubDemandForecasterExamples(18);
        stubStockoutExamples(11);
        stubShrinkageExamples(8);
        stubPaymentAnomalyExamples(8);
        stubPaymentDistressExamples(14);
        stubRepPerformanceExamples(10);
        stubDataQualityExamples(13);

        trainer.trainAllModels(emptyBundle(), DISTRIBUTOR_ID);

        verify(modelRegistry, never()).registerModel(
                eq(DataPhaseTracker.MODEL_CREDIT_CLASSIFIER), any(), any(), any());
    }

    @Test
    void tooFewAnomalyExamples_skipsAnomalyModel() {
        stubClassificationExamples(15);
        stubRegressionExamples(12);
        stubDemandForecasterExamples(18);
        stubStockoutExamples(11);

        // Only 2 shrinkage examples (below MIN_ANOMALY_EXAMPLES = 5)
        lenient().when(featureStore.buildShrinkageDetectorExamples(any()))
                .thenReturn(buildAnomalyList(2));
        lenient().when(dataMixer.buildTrainingDataset(
                        eq(DataPhaseTracker.MODEL_SHRINKAGE_DETECTOR), any(), any(), any()))
                .thenAnswer(inv -> inv.<List<?>>getArgument(3));

        stubPaymentAnomalyExamples(8);
        stubPaymentDistressExamples(14);
        stubRepPerformanceExamples(10);
        stubDataQualityExamples(13);

        SyntheticModelTrainer.SyntheticTrainingResult result =
                trainer.trainAllModels(emptyBundle(), DISTRIBUTOR_ID);

        assertThat(result.trainedModelIds())
                .doesNotContainKey(DataPhaseTracker.MODEL_SHRINKAGE_DETECTOR);
        // 8 others still trained
        assertThat(result.trainedModelIds()).hasSize(8);
    }

    // ── Error isolation ──────────────────────────────────────────────────────

    @Test
    void registryException_capturedInErrors_otherModelsContinue() {
        stubClassificationExamples(15);
        stubRegressionExamples(12);
        stubDemandForecasterExamples(18);
        stubStockoutExamples(11);
        stubShrinkageExamples(8);
        stubPaymentAnomalyExamples(8);
        stubPaymentDistressExamples(14);
        stubRepPerformanceExamples(10);
        stubDataQualityExamples(13);

        // credit_classifier registration throws; others succeed
        AIModelRegistry mockReg = registryEntry();
        when(modelRegistry.registerModel(
                eq(DataPhaseTracker.MODEL_CREDIT_CLASSIFIER), any(), any(), any()))
                .thenThrow(new RuntimeException("DB down"));
        when(modelRegistry.registerModel(
                argThat(n -> !n.equals(DataPhaseTracker.MODEL_CREDIT_CLASSIFIER)),
                any(), any(), any()))
                .thenReturn(mockReg);
        when(modelRegistry.promoteToActive(any())).thenReturn(mockReg);

        SyntheticModelTrainer.SyntheticTrainingResult result =
                trainer.trainAllModels(emptyBundle(), DISTRIBUTOR_ID);

        assertThat(result.success()).isFalse();
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0)).contains(DataPhaseTracker.MODEL_CREDIT_CLASSIFIER);
        // The other 8 models still trained
        assertThat(result.trainedModelIds()).hasSize(8);
    }

    @Test
    void featureStoreException_capturedInErrors_otherModelsContinue() {
        // featureStore throws for shrinkage_detector; others fine
        stubClassificationExamples(15);
        stubRegressionExamples(12);
        stubDemandForecasterExamples(18);
        stubStockoutExamples(11);
        lenient().when(featureStore.buildShrinkageDetectorExamples(any()))
                .thenThrow(new RuntimeException("compute error"));
        stubPaymentAnomalyExamples(8);
        stubPaymentDistressExamples(14);
        stubRepPerformanceExamples(10);
        stubDataQualityExamples(13);

        SyntheticModelTrainer.SyntheticTrainingResult result =
                trainer.trainAllModels(emptyBundle(), DISTRIBUTOR_ID);

        assertThat(result.success()).isFalse();
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0)).contains(DataPhaseTracker.MODEL_SHRINKAGE_DETECTOR);
        assertThat(result.trainedModelIds()).hasSize(8);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void stubAllModels() {
        stubClassificationExamples(20);
        stubRegressionExamples(20);
        stubDemandForecasterExamples(20);
        stubStockoutExamples(15);
        stubShrinkageExamples(10);
        stubPaymentAnomalyExamples(10);
        stubPaymentDistressExamples(20);
        stubRepPerformanceExamples(15);
        stubDataQualityExamples(20);
    }

    @SuppressWarnings("unchecked")
    private void stubClassificationExamples(int count) {
        List<Example<Label>> examples = buildClassificationList(count);
        lenient().when(featureStore.buildCreditClassifierExamples(any())).thenReturn(examples);
        lenient().when(dataMixer.buildTrainingDataset(
                        eq(DataPhaseTracker.MODEL_CREDIT_CLASSIFIER), any(), any(), any()))
                .thenAnswer(inv -> inv.<List<?>>getArgument(3));

        Model<Label> mockModel = mock(Model.class, withSettings().serializable());
        lenient().when(classificationTrainer.train(any())).thenReturn(mockModel);

        AIModelRegistry reg = registryEntry();
        lenient().when(modelRegistry.registerModel(
                eq(DataPhaseTracker.MODEL_CREDIT_CLASSIFIER), any(), any(), any())).thenReturn(reg);
        lenient().when(modelRegistry.promoteToActive(reg.getId())).thenReturn(reg);
    }

    @SuppressWarnings("unchecked")
    private void stubRegressionExamples(int count) {
        List<Example<Regressor>> examples = buildRegressionList(count);
        lenient().when(featureStore.buildCreditLimitRegressorExamples(any())).thenReturn(examples);
        lenient().when(dataMixer.buildTrainingDataset(
                        eq(DataPhaseTracker.MODEL_CREDIT_LIMIT_REGRESSOR), any(), any(), any()))
                .thenAnswer(inv -> inv.<List<?>>getArgument(3));

        Model<Regressor> mockModel = mock(Model.class, withSettings().serializable());
        lenient().when(regressionTrainer.train(any())).thenReturn(mockModel);

        AIModelRegistry reg = registryEntry();
        lenient().when(modelRegistry.registerModel(
                eq(DataPhaseTracker.MODEL_CREDIT_LIMIT_REGRESSOR), any(), any(), any())).thenReturn(reg);
        lenient().when(modelRegistry.promoteToActive(reg.getId())).thenReturn(reg);
    }

    @SuppressWarnings("unchecked")
    private void stubDemandForecasterExamples(int count) {
        List<Example<Regressor>> examples = buildRegressionList(count);
        lenient().when(featureStore.buildDemandForecasterExamples(any())).thenReturn(examples);
        lenient().when(dataMixer.buildTrainingDataset(
                        eq(DataPhaseTracker.MODEL_DEMAND_FORECASTER), any(), any(), any()))
                .thenAnswer(inv -> inv.<List<?>>getArgument(3));

        Model<Regressor> mockModel = mock(Model.class, withSettings().serializable());
        lenient().when(regressionTrainer.train(any())).thenReturn(mockModel);

        AIModelRegistry reg = registryEntry();
        lenient().when(modelRegistry.registerModel(
                eq(DataPhaseTracker.MODEL_DEMAND_FORECASTER), any(), any(), any())).thenReturn(reg);
        lenient().when(modelRegistry.promoteToActive(reg.getId())).thenReturn(reg);
    }

    @SuppressWarnings("unchecked")
    private void stubStockoutExamples(int count) {
        List<Example<Label>> examples = buildClassificationList(count);
        lenient().when(featureStore.buildStockoutPredictorExamples(any())).thenReturn(examples);
        lenient().when(dataMixer.buildTrainingDataset(
                        eq(DataPhaseTracker.MODEL_STOCKOUT_PREDICTOR), any(), any(), any()))
                .thenAnswer(inv -> inv.<List<?>>getArgument(3));

        Model<Label> mockModel = mock(Model.class, withSettings().serializable());
        lenient().when(classificationTrainer.train(any())).thenReturn(mockModel);

        AIModelRegistry reg = registryEntry();
        lenient().when(modelRegistry.registerModel(
                eq(DataPhaseTracker.MODEL_STOCKOUT_PREDICTOR), any(), any(), any())).thenReturn(reg);
        lenient().when(modelRegistry.promoteToActive(reg.getId())).thenReturn(reg);
    }

    @SuppressWarnings("unchecked")
    private void stubShrinkageExamples(int count) {
        List<Example<Event>> examples = buildAnomalyList(count);
        lenient().when(featureStore.buildShrinkageDetectorExamples(any())).thenReturn(examples);
        lenient().when(dataMixer.buildTrainingDataset(
                        eq(DataPhaseTracker.MODEL_SHRINKAGE_DETECTOR), any(), any(), any()))
                .thenAnswer(inv -> inv.<List<?>>getArgument(3));

        Model<Event> mockModel = mock(Model.class, withSettings().serializable());
        lenient().when(anomalyTrainer.train(any())).thenReturn(mockModel);

        AIModelRegistry reg = registryEntry();
        lenient().when(modelRegistry.registerModel(
                eq(DataPhaseTracker.MODEL_SHRINKAGE_DETECTOR), any(), any(), any())).thenReturn(reg);
        lenient().when(modelRegistry.promoteToActive(reg.getId())).thenReturn(reg);
    }

    @SuppressWarnings("unchecked")
    private void stubPaymentAnomalyExamples(int count) {
        List<Example<Event>> examples = buildAnomalyList(count);
        lenient().when(featureStore.buildPaymentAnomalyExamples(any())).thenReturn(examples);
        lenient().when(dataMixer.buildTrainingDataset(
                        eq(DataPhaseTracker.MODEL_PAYMENT_ANOMALY_DETECTOR), any(), any(), any()))
                .thenAnswer(inv -> inv.<List<?>>getArgument(3));

        Model<Event> mockModel = mock(Model.class, withSettings().serializable());
        // anomalyTrainer is shared by both shrinkage + payment; use lenient to avoid conflict
        lenient().when(anomalyTrainer.train(any())).thenReturn(mockModel);

        AIModelRegistry reg = registryEntry();
        lenient().when(modelRegistry.registerModel(
                eq(DataPhaseTracker.MODEL_PAYMENT_ANOMALY_DETECTOR), any(), any(), any())).thenReturn(reg);
        lenient().when(modelRegistry.promoteToActive(reg.getId())).thenReturn(reg);
    }

    @SuppressWarnings("unchecked")
    private void stubPaymentDistressExamples(int count) {
        List<Example<Label>> examples = buildClassificationList(count);
        lenient().when(featureStore.buildPaymentDistressExamples(any())).thenReturn(examples);
        lenient().when(dataMixer.buildTrainingDataset(
                        eq(DataPhaseTracker.MODEL_PAYMENT_DISTRESS_CLASSIFIER), any(), any(), any()))
                .thenAnswer(inv -> inv.<List<?>>getArgument(3));

        Model<Label> mockModel = mock(Model.class, withSettings().serializable());
        lenient().when(classificationTrainer.train(any())).thenReturn(mockModel);

        AIModelRegistry reg = registryEntry();
        lenient().when(modelRegistry.registerModel(
                eq(DataPhaseTracker.MODEL_PAYMENT_DISTRESS_CLASSIFIER), any(), any(), any())).thenReturn(reg);
        lenient().when(modelRegistry.promoteToActive(reg.getId())).thenReturn(reg);
    }

    @SuppressWarnings("unchecked")
    private void stubRepPerformanceExamples(int count) {
        List<Example<Label>> examples = buildClassificationList(count);
        lenient().when(featureStore.buildRepPerformancePredictorExamples(any())).thenReturn(examples);
        lenient().when(dataMixer.buildTrainingDataset(
                        eq(DataPhaseTracker.MODEL_REP_PERFORMANCE_PREDICTOR), any(), any(), any()))
                .thenAnswer(inv -> inv.<List<?>>getArgument(3));

        Model<Label> mockModel = mock(Model.class, withSettings().serializable());
        lenient().when(classificationTrainer.train(any())).thenReturn(mockModel);

        AIModelRegistry reg = registryEntry();
        lenient().when(modelRegistry.registerModel(
                eq(DataPhaseTracker.MODEL_REP_PERFORMANCE_PREDICTOR), any(), any(), any())).thenReturn(reg);
        lenient().when(modelRegistry.promoteToActive(reg.getId())).thenReturn(reg);
    }

    @SuppressWarnings("unchecked")
    private void stubDataQualityExamples(int count) {
        List<Example<Label>> examples = buildClassificationList(count);
        lenient().when(featureStore.buildDataQualityExamples(any())).thenReturn(examples);
        lenient().when(dataMixer.buildTrainingDataset(
                        eq(DataPhaseTracker.MODEL_DATA_QUALITY_DETECTOR), any(), any(), any()))
                .thenAnswer(inv -> inv.<List<?>>getArgument(3));

        Model<Label> mockModel = mock(Model.class, withSettings().serializable());
        lenient().when(classificationTrainer.train(any())).thenReturn(mockModel);

        AIModelRegistry reg = registryEntry();
        lenient().when(modelRegistry.registerModel(
                eq(DataPhaseTracker.MODEL_DATA_QUALITY_DETECTOR), any(), any(), any())).thenReturn(reg);
        lenient().when(modelRegistry.promoteToActive(reg.getId())).thenReturn(reg);
    }

    // ── Example factories ────────────────────────────────────────────────────

    /** Roughly 50/50 two-label split (DEFAULT / NO_DEFAULT). */
    private List<Example<Label>> buildClassificationList(int size) {
        List<Example<Label>> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(classificationExample(i % 2 == 0));
        }
        return list;
    }

    /** All NO_DEFAULT — forces single-class path. */
    private List<Example<Label>> buildSingleClassList(int size) {
        List<Example<Label>> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(classificationExample(false));
        }
        return list;
    }

    private List<Example<Regressor>> buildRegressionList(int size) {
        List<Example<Regressor>> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(regressionExample(10_000.0 + i * 1_000.0));
        }
        return list;
    }

    private List<Example<Event>> buildAnomalyList(int size) {
        List<Example<Event>> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(anomalyExample(i % 5 == 0));
        }
        return list;
    }

    private Example<Label> classificationExample(boolean defaulted) {
        Label label = new Label(defaulted ? "DEFAULT" : "NO_DEFAULT");
        return new ArrayExample<>(label,
                List.of(new org.tribuo.Feature("f1", defaulted ? 1.0 : 0.0)));
    }

    private Example<Regressor> regressionExample(double target) {
        Regressor regressor = new Regressor("DIM-0", target);
        return new ArrayExample<>(regressor,
                List.of(new org.tribuo.Feature("f1", target / 100_000.0)));
    }

    private Example<Event> anomalyExample(boolean anomalous) {
        Event event = anomalous
                ? new Event(Event.EventType.ANOMALOUS)
                : new Event(Event.EventType.EXPECTED);
        return new ArrayExample<>(event,
                List.of(new org.tribuo.Feature("f1", anomalous ? 1.0 : 0.0)));
    }

    private AIModelRegistry registryEntry() {
        return AIModelRegistry.builder()
                .id(UUID.randomUUID())
                .modelName("test_model")
                .modelVersion(1)
                .algorithm("test_algo")
                .status(ModelStatus.EVALUATING)
                .build();
    }

    private SyntheticDataBundle emptyBundle() {
        return SyntheticDataBundle.create(
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                1L, SyntheticDataConfig.defaultConfig(UUID.randomUUID(), 42L));
    }
}
