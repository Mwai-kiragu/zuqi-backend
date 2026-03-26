package com.zuqi.ai.model.tuning;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.tribuo.Example;
import org.tribuo.Model;
import org.tribuo.MutableDataset;
import org.tribuo.anomaly.AnomalyFactory;
import org.tribuo.anomaly.Event;
import org.tribuo.anomaly.evaluation.AnomalyEvaluation;
import org.tribuo.anomaly.evaluation.AnomalyEvaluator;
import org.tribuo.classification.Label;
import org.tribuo.classification.LabelFactory;
import org.tribuo.classification.evaluation.LabelEvaluation;
import org.tribuo.classification.evaluation.LabelEvaluator;
import org.tribuo.provenance.SimpleDataSourceProvenance;
import org.tribuo.regression.Regressor;
import org.tribuo.regression.RegressionFactory;
import org.tribuo.regression.evaluation.RegressionEvaluation;
import org.tribuo.regression.evaluation.RegressionEvaluator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Performs holdout validation after the final model is trained, gating promotion
 * on minimum performance thresholds.
 *
 * <h3>Thresholds</h3>
 * <ul>
 *   <li>Classifiers:       macro-F1 ≥ {@value #MIN_CLASSIFIER_F1}</li>
 *   <li>Regressors:        normalised RMSE ≤ {@value #MAX_REGRESSOR_NRMSE}
 *       (RMSE / std-dev of holdout targets; ≤ 1.0 means the model beats "predict the mean")</li>
 *   <li>Anomaly detectors: anomaly-class F1 ≥ {@value #MIN_ANOMALY_F1}</li>
 * </ul>
 *
 * <p>If the holdout set is too small or degenerate (single class, no anomalous events),
 * validation is skipped and a passing {@link ValidationResult#skipped} is returned so
 * the model is still promoted rather than silently blocked by a data artefact.
 */
@Component
@Slf4j
public class HoldoutValidator {

    /** Fraction of examples reserved for holdout evaluation. */
    static final double HOLDOUT_FRACTION     = 0.20;
    /** RNG seed for the holdout shuffle — different from the CV seed (42) to avoid overlap. */
    static final long   HOLDOUT_SEED         = 1234L;
    /** Minimum holdout examples required before running evaluation. */
    static final int    MIN_HOLDOUT_EXAMPLES = 5;

    // ── Promotion thresholds ──────────────────────────────────────────────
    /** Classifiers must achieve at least this macro-averaged F1 on the holdout set. */
    static final double MIN_CLASSIFIER_F1   = 0.60;
    /**
     * Regressors must achieve a normalised RMSE ≤ this value.
     * nRMSE = rmse / stddev(holdout targets); 1.0 is equivalent to R² ≥ 0.
     */
    static final double MAX_REGRESSOR_NRMSE = 1.0;
    /** Anomaly detectors must achieve at least this F1 on the ANOMALOUS class. */
    static final double MIN_ANOMALY_F1      = 0.50;

    // ── Split ─────────────────────────────────────────────────────────────

    /**
     * Shuffles {@code examples} deterministically and splits them into an 80 % training
     * partition and a 20 % holdout partition.
     *
     * @param <T>      element type
     * @param examples the full example list to split
     * @return an immutable {@link HoldoutSplit} with train and holdout sublists
     */
    public <T> HoldoutSplit<T> split(List<T> examples) {
        List<T> shuffled = new ArrayList<>(examples);
        Collections.shuffle(shuffled, new Random(HOLDOUT_SEED));
        int splitAt = (int) Math.round(shuffled.size() * (1.0 - HOLDOUT_FRACTION));
        return new HoldoutSplit<>(
                new ArrayList<>(shuffled.subList(0, splitAt)),
                new ArrayList<>(shuffled.subList(splitAt, shuffled.size())));
    }

    // ── Classification validation ─────────────────────────────────────────

    /**
     * Evaluates a trained classification model against a holdout set.
     * Returns a skipped result (passes) when the holdout is too small or single-class.
     *
     * @param model     the trained classifier to evaluate
     * @param holdout   held-out examples never seen during training or CV
     * @param modelName used for logging
     * @return holdout validation result — passed when macro-F1 ≥ {@value #MIN_CLASSIFIER_F1}
     */
    public ValidationResult validateClassifier(Model<Label> model,
                                               List<Example<Label>> holdout,
                                               String modelName) {
        if (holdout.size() < MIN_HOLDOUT_EXAMPLES) {
            log.warn("[Holdout] {} — too few holdout examples ({}), skipping validation",
                    modelName, holdout.size());
            return ValidationResult.skipped("macro_f1");
        }

        long distinctClasses = holdout.stream()
                .map(ex -> ex.getOutput().getLabel()).distinct().count();
        if (distinctClasses < 2) {
            log.warn("[Holdout] {} — single-class holdout, skipping validation", modelName);
            return ValidationResult.skipped("macro_f1");
        }

        try {
            MutableDataset<Label> holdoutDs = buildClassificationDataset(holdout, modelName + "_holdout");
            LabelEvaluation eval = new LabelEvaluator().evaluate(model, holdoutDs);
            double f1 = eval.macroAveragedF1();
            boolean passed = f1 >= MIN_CLASSIFIER_F1;
            log.info("[Holdout] {} — macro_f1={:.4f}, threshold={}, passed={}",
                    modelName, f1, MIN_CLASSIFIER_F1, passed);
            return new ValidationResult(passed, "macro_f1", f1, MIN_CLASSIFIER_F1);
        } catch (Exception e) {
            log.warn("[Holdout] {} — evaluation failed: {}, skipping validation",
                    modelName, e.getMessage());
            return ValidationResult.skipped("macro_f1");
        }
    }

    // ── Regression validation ─────────────────────────────────────────────

    /**
     * Evaluates a trained regression model against a holdout set using normalised RMSE.
     * nRMSE = rmse / stddev(targets); a value ≤ 1.0 means the model outperforms the
     * "predict the mean" baseline (equivalent to R² ≥ 0).
     *
     * @param model     the trained regressor to evaluate
     * @param holdout   held-out examples never seen during training or CV
     * @param modelName used for logging
     * @return holdout validation result — passed when nRMSE ≤ {@value #MAX_REGRESSOR_NRMSE}
     */
    public ValidationResult validateRegressor(Model<Regressor> model,
                                              List<Example<Regressor>> holdout,
                                              String modelName) {
        if (holdout.size() < MIN_HOLDOUT_EXAMPLES) {
            log.warn("[Holdout] {} — too few holdout examples ({}), skipping validation",
                    modelName, holdout.size());
            return ValidationResult.skipped("nrmse");
        }

        try {
            // No-skill baseline: std dev of holdout targets
            double mean = holdout.stream()
                    .mapToDouble(ex -> ex.getOutput().getValues()[0])
                    .average().orElse(0.0);
            double variance = holdout.stream()
                    .mapToDouble(ex -> Math.pow(ex.getOutput().getValues()[0] - mean, 2))
                    .average().orElse(0.0);
            double noSkillRmse = Math.sqrt(variance);

            MutableDataset<Regressor> holdoutDs = buildRegressionDataset(holdout, modelName + "_holdout");
            RegressionEvaluation eval = new RegressionEvaluator().evaluate(model, holdoutDs);
            double rmse = eval.averageRMSE();

            // Normalise: if std dev is zero (all targets identical), use raw RMSE < 1 as gate
            double nRmse = noSkillRmse > 0 ? rmse / noSkillRmse : (rmse < 1.0 ? 0.0 : 2.0);
            boolean passed = nRmse <= MAX_REGRESSOR_NRMSE;
            log.info("[Holdout] {} — nRmse={:.4f} (rmse={:.4f} / noSkill={:.4f}), threshold={}, passed={}",
                    modelName, nRmse, rmse, noSkillRmse, MAX_REGRESSOR_NRMSE, passed);
            return new ValidationResult(passed, "nrmse", nRmse, MAX_REGRESSOR_NRMSE);
        } catch (Exception e) {
            log.warn("[Holdout] {} — evaluation failed: {}, skipping validation",
                    modelName, e.getMessage());
            return ValidationResult.skipped("nrmse");
        }
    }

    // ── Anomaly detection validation ──────────────────────────────────────

    /**
     * Evaluates a trained anomaly detector against a holdout set containing both
     * EXPECTED and ANOMALOUS events.
     * Returns a skipped result when there are no ANOMALOUS events in the holdout
     * (F1 would be undefined).
     *
     * @param model     the trained anomaly detector to evaluate
     * @param holdout   held-out examples (EXPECTED + ANOMALOUS) never seen during training or CV
     * @param modelName used for logging
     * @return holdout validation result — passed when anomaly F1 ≥ {@value #MIN_ANOMALY_F1}
     */
    public ValidationResult validateAnomalyDetector(Model<Event> model,
                                                     List<Example<Event>> holdout,
                                                     String modelName) {
        if (holdout.size() < MIN_HOLDOUT_EXAMPLES) {
            log.warn("[Holdout] {} — too few holdout examples ({}), skipping validation",
                    modelName, holdout.size());
            return ValidationResult.skipped("anomaly_f1");
        }

        long anomalousCount = holdout.stream()
                .filter(ex -> ex.getOutput().getType() == Event.EventType.ANOMALOUS)
                .count();
        if (anomalousCount == 0) {
            log.warn("[Holdout] {} — no ANOMALOUS events in holdout, skipping validation", modelName);
            return ValidationResult.skipped("anomaly_f1");
        }

        try {
            AnomalyFactory factory = new AnomalyFactory();
            MutableDataset<Event> holdoutDs = new MutableDataset<>(
                    new SimpleDataSourceProvenance(modelName + "_holdout", factory), factory);
            holdout.forEach(holdoutDs::add);

            AnomalyEvaluation eval = new AnomalyEvaluator().evaluate(model, holdoutDs);
            double f1 = eval.getF1();
            boolean passed = f1 >= MIN_ANOMALY_F1;
            log.info("[Holdout] {} — anomaly_f1={:.4f}, threshold={}, passed={}",
                    modelName, f1, MIN_ANOMALY_F1, passed);
            return new ValidationResult(passed, "anomaly_f1", f1, MIN_ANOMALY_F1);
        } catch (Exception e) {
            log.warn("[Holdout] {} — evaluation failed: {}, skipping validation",
                    modelName, e.getMessage());
            return ValidationResult.skipped("anomaly_f1");
        }
    }

    // ── Dataset builders ──────────────────────────────────────────────────

    private MutableDataset<Label> buildClassificationDataset(List<Example<Label>> examples, String name) {
        LabelFactory factory = new LabelFactory();
        MutableDataset<Label> ds = new MutableDataset<>(
                new SimpleDataSourceProvenance(name, factory), factory);
        examples.forEach(ds::add);
        return ds;
    }

    private MutableDataset<Regressor> buildRegressionDataset(List<Example<Regressor>> examples, String name) {
        RegressionFactory factory = new RegressionFactory();
        MutableDataset<Regressor> ds = new MutableDataset<>(
                new SimpleDataSourceProvenance(name, factory), factory);
        examples.forEach(ds::add);
        return ds;
    }

    // ── Result type ───────────────────────────────────────────────────────

    /**
     * A train/holdout split of an example list.
     *
     * @param <T>     element type
     * @param train   80 % of shuffled examples — used for CV and final model training
     * @param holdout 20 % of shuffled examples — never seen by the model before evaluation
     */
    public record HoldoutSplit<T>(List<T> train, List<T> holdout) {}
}
