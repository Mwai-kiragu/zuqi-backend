package com.zuqi.ai.model.tuning;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tribuo.Example;
import org.tribuo.Model;
import org.tribuo.anomaly.Event;
import org.tribuo.classification.Label;
import org.tribuo.impl.ArrayExample;
import org.tribuo.regression.Regressor;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link HoldoutValidator}.
 *
 * <p>Tests that require training a real Tribuo model (XGBoost via JNI) are excluded
 * from this unit suite. The evaluation path is verified by the tuning integration
 * tests and the {@link ModelTuningServiceTest} gate tests.
 *
 * <p>This test class covers:
 * <ul>
 *   <li>Split fractions and determinism</li>
 *   <li>Guard paths: too-few examples, single class, no anomalous events</li>
 *   <li>{@link ValidationResult#skipped} sentinel behaviour</li>
 * </ul>
 */
class HoldoutValidatorTest {

    private HoldoutValidator validator;

    @BeforeEach
    void setUp() {
        validator = new HoldoutValidator();
    }

    // ── split() ───────────────────────────────────────────────────────────

    @Test
    void split_returnsCorrectFractions() {
        List<Integer> input = range(100);

        HoldoutValidator.HoldoutSplit<Integer> split = validator.split(input);

        // 80 % train, 20 % holdout (exact for multiples of 5)
        assertThat(split.train()).hasSize(80);
        assertThat(split.holdout()).hasSize(20);
    }

    @Test
    void split_noExamplesLost() {
        List<Integer> input = range(47); // non-round number
        HoldoutValidator.HoldoutSplit<Integer> split = validator.split(input);

        assertThat(split.train().size() + split.holdout().size()).isEqualTo(47);
    }

    @Test
    void split_isDeterministic() {
        List<Integer> input = range(60);

        HoldoutValidator.HoldoutSplit<Integer> first  = validator.split(input);
        HoldoutValidator.HoldoutSplit<Integer> second = validator.split(input);

        assertThat(first.train()).isEqualTo(second.train());
        assertThat(first.holdout()).isEqualTo(second.holdout());
    }

    @Test
    void split_trainAndHoldoutAreDisjoint() {
        List<Integer> input = range(50);
        HoldoutValidator.HoldoutSplit<Integer> split = validator.split(input);

        List<Integer> union = new ArrayList<>(split.train());
        union.addAll(split.holdout());
        assertThat(union).containsExactlyInAnyOrderElementsOf(input);
    }

    // ── validateClassifier — guard paths ──────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void validateClassifier_tooFewExamples_returnsSkipped() {
        Model<Label> model = mock(Model.class);
        List<Example<Label>> holdout = twoClassExamples(
                HoldoutValidator.MIN_HOLDOUT_EXAMPLES - 1);

        ValidationResult result = validator.validateClassifier(model, holdout, "test_model");

        assertThat(result.wasSkipped()).isTrue();
        assertThat(result.passed()).isTrue();
        assertThat(result.metricName()).isEqualTo("macro_f1");
    }

    @Test
    @SuppressWarnings("unchecked")
    void validateClassifier_singleClass_returnsSkipped() {
        Model<Label> model = mock(Model.class);
        // All examples have the same label — use local variable so diamond can infer
        List<Example<Label>> holdout = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            ArrayExample<Label> ex = new ArrayExample<>(
                    new Label("HIGH"), new String[]{"f1"}, new double[]{i});
            holdout.add(ex);
        }

        ValidationResult result = validator.validateClassifier(model, holdout, "test_model");

        assertThat(result.wasSkipped()).isTrue();
        assertThat(result.passed()).isTrue();
    }

    // ── validateRegressor — guard paths ───────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void validateRegressor_tooFewExamples_returnsSkipped() {
        Model<Regressor> model = mock(Model.class);
        List<Example<Regressor>> holdout = regressionExamples(
                HoldoutValidator.MIN_HOLDOUT_EXAMPLES - 1);

        ValidationResult result = validator.validateRegressor(model, holdout, "test_model");

        assertThat(result.wasSkipped()).isTrue();
        assertThat(result.passed()).isTrue();
        assertThat(result.metricName()).isEqualTo("nrmse");
    }

    // ── validateAnomalyDetector — guard paths ─────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void validateAnomalyDetector_tooFewExamples_returnsSkipped() {
        Model<Event> model = mock(Model.class);
        List<Example<Event>> holdout = anomalyExamples(
                HoldoutValidator.MIN_HOLDOUT_EXAMPLES - 1, true);

        ValidationResult result = validator.validateAnomalyDetector(model, holdout, "test_model");

        assertThat(result.wasSkipped()).isTrue();
        assertThat(result.passed()).isTrue();
        assertThat(result.metricName()).isEqualTo("anomaly_f1");
    }

    @Test
    @SuppressWarnings("unchecked")
    void validateAnomalyDetector_noAnomalousEvents_returnsSkipped() {
        Model<Event> model = mock(Model.class);
        // All EXPECTED, no ANOMALOUS
        List<Example<Event>> holdout = anomalyExamples(10, false);

        ValidationResult result = validator.validateAnomalyDetector(model, holdout, "test_model");

        assertThat(result.wasSkipped()).isTrue();
        assertThat(result.passed()).isTrue();
    }

    // ── ValidationResult sentinel ─────────────────────────────────────────

    @Test
    void validationResult_skipped_hasNaNValues() {
        ValidationResult skipped = ValidationResult.skipped("macro_f1");

        assertThat(skipped.passed()).isTrue();
        assertThat(skipped.wasSkipped()).isTrue();
        assertThat(Double.isNaN(skipped.holdoutValue())).isTrue();
        assertThat(Double.isNaN(skipped.threshold())).isTrue();
    }

    @Test
    void validationResult_passed_isNotSkipped() {
        ValidationResult passed = new ValidationResult(true, "macro_f1", 0.75, 0.60);

        assertThat(passed.passed()).isTrue();
        assertThat(passed.wasSkipped()).isFalse();
        assertThat(passed.holdoutValue()).isEqualTo(0.75);
        assertThat(passed.threshold()).isEqualTo(0.60);
    }

    @Test
    void validationResult_failed_isNotPassedAndNotSkipped() {
        ValidationResult failed = new ValidationResult(false, "macro_f1", 0.45, 0.60);

        assertThat(failed.passed()).isFalse();
        assertThat(failed.wasSkipped()).isFalse();
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private List<Integer> range(int n) {
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < n; i++) list.add(i);
        return list;
    }

    private List<Example<Label>> twoClassExamples(int count) {
        List<Example<Label>> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String lbl = (i % 2 == 0) ? "HIGH" : "LOW";
            ArrayExample<Label> ex = new ArrayExample<>(
                    new Label(lbl), new String[]{"f1"}, new double[]{i});
            list.add(ex);
        }
        return list;
    }

    private List<Example<Regressor>> regressionExamples(int count) {
        List<Example<Regressor>> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ArrayExample<Regressor> ex = new ArrayExample<>(
                    new Regressor("target", i * 1.5), new String[]{"f1"}, new double[]{i});
            list.add(ex);
        }
        return list;
    }

    private List<Example<Event>> anomalyExamples(int count, boolean includeAnomalous) {
        List<Example<Event>> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Event output = (includeAnomalous && i % 5 == 0)
                    ? new Event(Event.EventType.ANOMALOUS)
                    : new Event(Event.EventType.EXPECTED);
            ArrayExample<Event> ex = new ArrayExample<>(
                    output, new String[]{"f1"}, new double[]{i});
            list.add(ex);
        }
        return list;
    }
}
