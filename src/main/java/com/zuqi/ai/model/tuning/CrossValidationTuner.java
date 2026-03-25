package com.zuqi.ai.model.tuning;

import com.oracle.labs.mlrg.olcut.util.Pair;
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
import org.tribuo.classification.evaluation.LabelEvaluation;
import org.tribuo.classification.evaluation.LabelEvaluator;
import org.tribuo.evaluation.CrossValidation;
import org.tribuo.provenance.SimpleDataSourceProvenance;
import org.tribuo.regression.Regressor;
import org.tribuo.regression.evaluation.RegressionEvaluation;
import org.tribuo.regression.evaluation.RegressionEvaluator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Runs k-fold cross-validation over a list of {@link CandidateConfig} instances
 * and returns the best configuration for each model family.
 *
 * <h3>Metrics</h3>
 * <ul>
 *   <li>Classification: macro-averaged F1 (higher is better)</li>
 *   <li>Regression: average RMSE (lower is better)</li>
 *   <li>Anomaly: F1 for the ANOMALOUS class (higher is better)</li>
 * </ul>
 *
 * <p>This is a stateless {@code @Component} — it holds no mutable state and
 * can be called safely from multiple async threads simultaneously.
 */
@Component
@Slf4j
public class CrossValidationTuner {

    private static final int DEFAULT_FOLDS = 5;
    private static final long CV_SEED = 42L;

    // ── Classification ──────────────────────────────────────────────────────

    /**
     * Tune a classification model by macro-averaged F1.
     *
     * @param modelName  used for logging
     * @param candidates list of candidate trainers to evaluate
     * @param dataset    labelled classification dataset
     * @return best configuration with its CV metric
     */
    public BestConfig<Label> tuneClassifier(
            String modelName,
            List<CandidateConfig<Label>> candidates,
            MutableDataset<Label> dataset) {

        log.info("[CVTuner] Tuning {} — {} candidates, {}-fold CV, {} examples",
                modelName, candidates.size(), DEFAULT_FOLDS, dataset.size());

        LabelEvaluator evaluator = new LabelEvaluator();
        double bestF1    = -1.0;
        int    bestIdx   = 0;

        for (int i = 0; i < candidates.size(); i++) {
            CandidateConfig<Label> candidate = candidates.get(i);
            try {
                CrossValidation<Label, LabelEvaluation> cv = new CrossValidation<>(
                        candidate.trainer(), dataset, evaluator, DEFAULT_FOLDS, CV_SEED);

                List<Pair<LabelEvaluation, Model<Label>>> results = cv.evaluate();
                double avgF1 = results.stream()
                        .mapToDouble(p -> p.getA().macroAveragedF1())
                        .average()
                        .orElse(0.0);

                log.debug("[CVTuner] {} candidate {}/{} — macroF1={} params={}",
                        modelName, i + 1, candidates.size(), avgF1,
                        candidate.hyperparameters());

                if (avgF1 > bestF1) {
                    bestF1  = avgF1;
                    bestIdx = i;
                }
            } catch (Exception e) {
                log.warn("[CVTuner] {} candidate {}/{} failed: {}",
                        modelName, i + 1, candidates.size(), e.getMessage());
            }
        }

        CandidateConfig<Label> best = candidates.get(bestIdx);
        log.info("[CVTuner] {} — best macroF1={} params={}", modelName, bestF1, best.hyperparameters());
        return new BestConfig<>(best, bestF1, "macro_f1", DEFAULT_FOLDS, candidates.size());
    }

    // ── Regression ──────────────────────────────────────────────────────────

    /**
     * Tune a regression model by average RMSE (lower is better).
     *
     * @param modelName  used for logging
     * @param candidates list of candidate trainers to evaluate
     * @param dataset    labelled regression dataset
     * @return best configuration with its CV metric
     */
    public BestConfig<Regressor> tuneRegressor(
            String modelName,
            List<CandidateConfig<Regressor>> candidates,
            MutableDataset<Regressor> dataset) {

        log.info("[CVTuner] Tuning {} — {} candidates, {}-fold CV, {} examples",
                modelName, candidates.size(), DEFAULT_FOLDS, dataset.size());

        RegressionEvaluator evaluator = new RegressionEvaluator();
        double bestRMSE  = Double.MAX_VALUE;
        int    bestIdx   = 0;

        for (int i = 0; i < candidates.size(); i++) {
            CandidateConfig<Regressor> candidate = candidates.get(i);
            try {
                CrossValidation<Regressor, RegressionEvaluation> cv = new CrossValidation<>(
                        candidate.trainer(), dataset, evaluator, DEFAULT_FOLDS, CV_SEED);

                List<Pair<RegressionEvaluation, Model<Regressor>>> results = cv.evaluate();
                double avgRMSE = results.stream()
                        .mapToDouble(p -> p.getA().averageRMSE())
                        .average()
                        .orElse(Double.MAX_VALUE);

                log.debug("[CVTuner] {} candidate {}/{} — RMSE={} params={}",
                        modelName, i + 1, candidates.size(), avgRMSE,
                        candidate.hyperparameters());

                if (avgRMSE < bestRMSE) {
                    bestRMSE = avgRMSE;
                    bestIdx  = i;
                }
            } catch (Exception e) {
                log.warn("[CVTuner] {} candidate {}/{} failed: {}",
                        modelName, i + 1, candidates.size(), e.getMessage());
            }
        }

        CandidateConfig<Regressor> best = candidates.get(bestIdx);
        log.info("[CVTuner] {} — best RMSE={} params={}", modelName, bestRMSE, best.hyperparameters());
        return new BestConfig<>(best, bestRMSE, "avg_rmse", DEFAULT_FOLDS, candidates.size());
    }

    // ── Anomaly ─────────────────────────────────────────────────────────────

    /**
     * Tune an anomaly detection model by F1 on the ANOMALOUS class (higher is better).
     *
     * <p>Uses manual fold splitting rather than Tribuo's {@link CrossValidation} because
     * LibSVMAnomalyTrainer requires EXPECTED-only training data, but meaningful F1
     * evaluation requires ANOMALOUS examples in the test fold.
     *
     * <p>For each fold:
     * <ol>
     *   <li>Train on EXPECTED-only examples from all other folds.</li>
     *   <li>Evaluate on all examples in the held-out fold (EXPECTED + ANOMALOUS).</li>
     * </ol>
     *
     * @param modelName   used for logging
     * @param candidates  list of candidate trainers to evaluate
     * @param allExamples all examples including both EXPECTED and ANOMALOUS
     * @return best configuration with its CV metric
     */
    public BestConfig<Event> tuneAnomalyDetector(
            String modelName,
            List<CandidateConfig<Event>> candidates,
            List<Example<Event>> allExamples) {

        log.info("[CVTuner] Tuning {} — {} candidates, {}-fold CV, {} examples ({} anomalous)",
                modelName, candidates.size(), DEFAULT_FOLDS, allExamples.size(),
                allExamples.stream().filter(e -> e.getOutput().getType() == Event.EventType.ANOMALOUS).count());

        List<Example<Event>> shuffled = new ArrayList<>(allExamples);
        Collections.shuffle(shuffled, new Random(CV_SEED));

        AnomalyFactory   factory   = new AnomalyFactory();
        AnomalyEvaluator evaluator = new AnomalyEvaluator();
        double bestF1  = -1.0;
        int    bestIdx = 0;

        for (int i = 0; i < candidates.size(); i++) {
            CandidateConfig<Event> candidate = candidates.get(i);
            try {
                double totalF1      = 0.0;
                int    successFolds = 0;

                for (int fold = 0; fold < DEFAULT_FOLDS; fold++) {
                    int foldStart = (int)((long) fold       * shuffled.size() / DEFAULT_FOLDS);
                    int foldEnd   = (int)((long)(fold + 1)  * shuffled.size() / DEFAULT_FOLDS);

                    // Test fold: all examples in this slice
                    List<Example<Event>> testExamples = shuffled.subList(foldStart, foldEnd);

                    // Train fold: remaining examples, EXPECTED only (LibSVM requirement)
                    List<Example<Event>> trainExpected = new ArrayList<>();
                    for (int j = 0; j < shuffled.size(); j++) {
                        if (j < foldStart || j >= foldEnd) {
                            Example<Event> ex = shuffled.get(j);
                            if (ex.getOutput().getType() == Event.EventType.EXPECTED) {
                                trainExpected.add(ex);
                            }
                        }
                    }

                    if (trainExpected.size() < 5) {
                        continue; // not enough normal examples to train this fold
                    }

                    MutableDataset<Event> trainDs = new MutableDataset<>(
                            new SimpleDataSourceProvenance(modelName + "_train_fold" + fold, factory), factory);
                    trainExpected.forEach(trainDs::add);

                    MutableDataset<Event> testDs = new MutableDataset<>(
                            new SimpleDataSourceProvenance(modelName + "_test_fold" + fold, factory), factory);
                    testExamples.forEach(testDs::add);

                    Model<Event> model = candidate.trainer().train(trainDs);
                    AnomalyEvaluation eval = evaluator.evaluate(model, testDs);
                    totalF1 += eval.getF1();
                    successFolds++;
                }

                double avgF1 = successFolds > 0 ? totalF1 / successFolds : 0.0;
                log.debug("[CVTuner] {} candidate {}/{} — F1={} params={}",
                        modelName, i + 1, candidates.size(), avgF1, candidate.hyperparameters());

                if (avgF1 > bestF1) {
                    bestF1  = avgF1;
                    bestIdx = i;
                }
            } catch (Exception e) {
                log.warn("[CVTuner] {} candidate {}/{} failed: {}",
                        modelName, i + 1, candidates.size(), e.getMessage());
            }
        }

        CandidateConfig<Event> best = candidates.get(bestIdx);
        log.info("[CVTuner] {} — best F1={} params={}", modelName, bestF1, best.hyperparameters());
        return new BestConfig<>(best, bestF1, "anomaly_f1", DEFAULT_FOLDS, candidates.size());
    }

    // ── Result type ─────────────────────────────────────────────────────────

    /**
     * The best candidate configuration and its cross-validated metric value.
     *
     * @param <T> Tribuo output type
     */
    public record BestConfig<T extends org.tribuo.Output<T>>(
            CandidateConfig<T> config,
            double             metricValue,
            String             metricName,
            int                numFolds,
            int                candidatesEvaluated) {}
}
