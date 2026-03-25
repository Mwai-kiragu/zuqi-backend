package com.zuqi.ai.model.tuning;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tribuo.Example;
import org.tribuo.Model;
import org.tribuo.MutableDataset;
import org.tribuo.Trainer;
import org.tribuo.anomaly.Event;
import org.tribuo.classification.Label;
import org.tribuo.classification.LabelFactory;
import org.tribuo.impl.ArrayExample;
import org.tribuo.provenance.SimpleDataSourceProvenance;
import org.tribuo.regression.Regressor;
import org.tribuo.regression.RegressionFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CrossValidationTuner}.
 *
 * Uses Mockito-mocked trainers and tiny (≥5 example) datasets so that
 * the XGBoost / LibSVM JNI is never loaded.
 *
 * The mocked trainer always returns {@code null} for the model, which means
 * Tribuo CrossValidation cannot actually run — so instead we test:
 *  1. The tuner falls back gracefully when every candidate throws.
 *  2. The returned {@code BestConfig} always references the candidate list.
 *  3. The metric name is correct per model family.
 *
 * Integration-level tests (with real XGBoost training) are out of scope
 * for unit tests; they run as part of the full seeding integration test.
 */
@ExtendWith(MockitoExtension.class)
class CrossValidationTunerTest {

    @Mock Trainer<Label>    mockClassifTrainer;
    @Mock Trainer<Regressor> mockRegTrainer;
    @Mock Trainer<Event>    mockAnomalyTrainer;

    @Mock Model<Label>    mockLabelModel;
    @Mock Model<Regressor> mockRegModel;
    @Mock Model<Event>    mockEventModel;

    private CrossValidationTuner tuner;

    @BeforeEach
    void setUp() {
        tuner = new CrossValidationTuner();
    }

    // ── Classifier ──────────────────────────────────────────────────────────

    @Test
    void tuneClassifier_withFailingTrainer_returnsFallbackOnIndex0() {
        // Arrange: all candidates fail during CV (train() throws)
        when(mockClassifTrainer.train(any(), any(), anyInt()))
                .thenThrow(new RuntimeException("XGBoost not available in test"));

        List<CandidateConfig<Label>> candidates = List.of(
                new CandidateConfig<>(mockClassifTrainer, Map.of("num_rounds", 50, "eta", 0.3)),
                new CandidateConfig<>(mockClassifTrainer, Map.of("num_rounds", 100, "eta", 0.1))
        );

        MutableDataset<Label> dataset = buildLabelDataset(20);

        // Act: tuner should not throw even if all candidates fail
        CrossValidationTuner.BestConfig<Label> best = tuner.tuneClassifier("test_model", candidates, dataset);

        // Assert: always returns a valid BestConfig
        assertThat(best).isNotNull();
        assertThat(best.config()).isIn(candidates);
        assertThat(best.metricName()).isEqualTo("macro_f1");
        assertThat(best.numFolds()).isEqualTo(5);
        assertThat(best.candidatesEvaluated()).isEqualTo(2);
    }

    @Test
    void tuneClassifier_metricNameIsMacroF1() {
        // Act with failing trainer (fallback path)
        List<CandidateConfig<Label>> candidates = List.of(
                new CandidateConfig<>(mockClassifTrainer, Map.of("num_rounds", 50))
        );
        when(mockClassifTrainer.train(any(), any(), anyInt()))
                .thenThrow(new RuntimeException("not available"));

        CrossValidationTuner.BestConfig<Label> best = tuner.tuneClassifier(
                "credit_classifier", candidates, buildLabelDataset(10));

        assertThat(best.metricName()).isEqualTo("macro_f1");
    }

    // ── Regressor ───────────────────────────────────────────────────────────

    @Test
    void tuneRegressor_withFailingTrainer_returnsFallbackOnIndex0() {
        when(mockRegTrainer.train(any(), any(), anyInt()))
                .thenThrow(new RuntimeException("XGBoost not available in test"));

        List<CandidateConfig<Regressor>> candidates = List.of(
                new CandidateConfig<>(mockRegTrainer, Map.of("num_rounds", 50, "eta", 0.3)),
                new CandidateConfig<>(mockRegTrainer, Map.of("num_rounds", 100, "eta", 0.1))
        );

        CrossValidationTuner.BestConfig<Regressor> best = tuner.tuneRegressor(
                "demand_forecaster", candidates, buildRegressorDataset(20));

        assertThat(best).isNotNull();
        assertThat(best.config()).isIn(candidates);
        assertThat(best.metricName()).isEqualTo("avg_rmse");
        assertThat(best.numFolds()).isEqualTo(5);
        assertThat(best.candidatesEvaluated()).isEqualTo(2);
    }

    @Test
    void tuneRegressor_metricNameIsAvgRmse() {
        when(mockRegTrainer.train(any(), any(), anyInt()))
                .thenThrow(new RuntimeException("not available"));

        List<CandidateConfig<Regressor>> candidates = List.of(
                new CandidateConfig<>(mockRegTrainer, Map.of("num_rounds", 50))
        );

        CrossValidationTuner.BestConfig<Regressor> best = tuner.tuneRegressor(
                "credit_limit_regressor", candidates, buildRegressorDataset(10));

        assertThat(best.metricName()).isEqualTo("avg_rmse");
    }

    // ── Anomaly ─────────────────────────────────────────────────────────────

    @Test
    void tuneAnomalyDetector_withFailingTrainer_returnsFallbackOnIndex0() {
        when(mockAnomalyTrainer.train(any(), any(), anyInt()))
                .thenThrow(new RuntimeException("LibSVM not available in test"));

        List<CandidateConfig<Event>> candidates = List.of(
                new CandidateConfig<>(mockAnomalyTrainer, Map.of("nu", 0.1, "gamma", 0.5)),
                new CandidateConfig<>(mockAnomalyTrainer, Map.of("nu", 0.2, "gamma", 0.5))
        );

        CrossValidationTuner.BestConfig<Event> best = tuner.tuneAnomalyDetector(
                "shrinkage_detector", candidates, buildAnomalyDataset(10));

        assertThat(best).isNotNull();
        assertThat(best.config()).isIn(candidates);
        assertThat(best.metricName()).isEqualTo("anomaly_f1");
        assertThat(best.numFolds()).isEqualTo(5);
        assertThat(best.candidatesEvaluated()).isEqualTo(2);
    }

    @Test
    void tuneAnomalyDetector_metricNameIsAnomalyF1() {
        when(mockAnomalyTrainer.train(any(), any(), anyInt()))
                .thenThrow(new RuntimeException("not available"));

        List<CandidateConfig<Event>> candidates = List.of(
                new CandidateConfig<>(mockAnomalyTrainer, Map.of("nu", 0.1))
        );

        CrossValidationTuner.BestConfig<Event> best = tuner.tuneAnomalyDetector(
                "payment_anomaly_detector", candidates, buildAnomalyDataset(10));

        assertThat(best.metricName()).isEqualTo("anomaly_f1");
    }

    // ── BestConfig record ────────────────────────────────────────────────────

    @Test
    void bestConfig_storesAllFields() {
        CandidateConfig<Label> candidate =
                new CandidateConfig<>(mockClassifTrainer, Map.of("num_rounds", 100));

        CrossValidationTuner.BestConfig<Label> best =
                new CrossValidationTuner.BestConfig<>(candidate, 0.87, "macro_f1", 5, 3);

        assertThat(best.config()).isSameAs(candidate);
        assertThat(best.metricValue()).isEqualTo(0.87);
        assertThat(best.metricName()).isEqualTo("macro_f1");
        assertThat(best.numFolds()).isEqualTo(5);
        assertThat(best.candidatesEvaluated()).isEqualTo(3);
    }

    // ── Dataset builders ─────────────────────────────────────────────────────

    private static MutableDataset<Label> buildLabelDataset(int n) {
        SimpleDataSourceProvenance prov = new SimpleDataSourceProvenance("test", new LabelFactory());
        MutableDataset<Label> ds = new MutableDataset<>(prov, new LabelFactory());
        for (int i = 0; i < n; i++) {
            String labelStr = (i % 2 == 0) ? "CLASS_A" : "CLASS_B";
            ArrayExample<Label> ex = new ArrayExample<>(
                    new Label(labelStr),
                    new String[]{"f1", "f2"},
                    new double[]{i * 0.1, i * 0.2});
            ds.add(ex);
        }
        return ds;
    }

    private static MutableDataset<Regressor> buildRegressorDataset(int n) {
        SimpleDataSourceProvenance prov = new SimpleDataSourceProvenance("test", new RegressionFactory());
        MutableDataset<Regressor> ds = new MutableDataset<>(prov, new RegressionFactory());
        for (int i = 0; i < n; i++) {
            ArrayExample<Regressor> ex = new ArrayExample<>(
                    new Regressor("target", i * 10.0),
                    new String[]{"f1", "f2"},
                    new double[]{i * 0.1, i * 0.2});
            ds.add(ex);
        }
        return ds;
    }

    private static List<org.tribuo.Example<Event>> buildAnomalyDataset(int n) {
        List<org.tribuo.Example<Event>> examples = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Event event = new Event((i % 5 == 0) ? Event.EventType.ANOMALOUS : Event.EventType.EXPECTED);
            ArrayExample<Event> ex = new ArrayExample<>(
                    event,
                    new String[]{"f1", "f2"},
                    new double[]{i * 0.1, i * 0.2});
            examples.add(ex);
        }
        return examples;
    }
}
