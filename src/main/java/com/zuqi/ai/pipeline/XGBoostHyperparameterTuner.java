package com.zuqi.ai.pipeline;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.tribuo.Dataset;
import org.tribuo.Model;
import org.tribuo.MutableDataset;
import org.tribuo.classification.Label;
import org.tribuo.classification.LabelFactory;
import org.tribuo.classification.evaluation.LabelEvaluation;
import org.tribuo.classification.evaluation.LabelEvaluator;
import org.tribuo.classification.xgboost.XGBoostClassificationTrainer;
import org.tribuo.provenance.SimpleDataSourceProvenance;
import org.tribuo.regression.Regressor;
import org.tribuo.regression.RegressionFactory;
import org.tribuo.regression.evaluation.RegressionEvaluation;
import org.tribuo.regression.evaluation.RegressionEvaluator;
import org.tribuo.regression.xgboost.XGBoostRegressionTrainer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * XGBoost hyperparameter tuner using grid search with k-fold cross-validation.
 *
 * <p>Grid:
 * <ul>
 *   <li>{@code numRounds} ∈ {50, 100, 200}</li>
 *   <li>{@code eta} (learning rate) ∈ {0.1, 0.2, 0.3}</li>
 *   <li>{@code maxDepth} ∈ {4, 6, 8}</li>
 * </ul>
 * 27 combinations × 3 folds = 81 training runs per call. Fast for datasets ≤1 000 examples.
 *
 * <p>Usage in a regression pipeline:
 * <pre>{@code
 * TuningResult t = hyperparameterTuner.tuneRegressor(trainDataset);
 * XGBoostRegressionTrainer best = new XGBoostRegressionTrainer(
 *     XGBoostRegressionTrainer.RegressionType.LINEAR, t.bestNumRounds(), t.bestParams());
 * Model<Regressor> model = best.train(trainDataset);
 * }</pre>
 *
 * <p>Usage in a classification pipeline:
 * <pre>{@code
 * TuningResult t = hyperparameterTuner.tuneClassifier(trainDataset, "POSITIVE_LABEL");
 * XGBoostClassificationTrainer best = new XGBoostClassificationTrainer(t.bestNumRounds(), t.bestParams());
 * Model<Label> model = best.train(trainDataset);
 * }</pre>
 */
@Component
@Slf4j
public class XGBoostHyperparameterTuner {

    // ── Grid ─────────────────────────────────────────────────────────────────

    private static final int[]    ROUNDS_GRID = {50, 100, 200};
    private static final double[] ETA_GRID    = {0.1, 0.2, 0.3};
    private static final int[]    DEPTH_GRID  = {4, 6, 8};
    private static final int      K_FOLDS     = 3;

    // ── Combined tune-and-train API (recommended for pipeline use) ───────────

    /**
     * Grid-search the best hyperparameters, then retrain on the full dataset with those params.
     * Returns both the trained model and the tuning metadata.
     *
     * <p>This combined API keeps pipeline code simple and makes testing straightforward:
     * tests can mock this single method to return a pre-trained mock model without
     * needing to intercept internal trainer construction.
     *
     * @param dataset full training dataset
     * @return trained model plus tuning metadata
     */
    public TunedModel<Regressor> tuneAndTrainRegressor(Dataset<Regressor> dataset) {
        TuningResult tuning = tuneRegressor(dataset);
        XGBoostRegressionTrainer trainer = new XGBoostRegressionTrainer(
                XGBoostRegressionTrainer.RegressionType.LINEAR,
                tuning.bestNumRounds(), tuning.bestParams());
        Model<Regressor> model = trainer.train(dataset);
        log.info("[HyperparamTuner] Final regressor trained: rounds={} eta={} maxDepth={}",
                tuning.bestNumRounds(), tuning.bestEta(), tuning.bestMaxDepth());
        return new TunedModel<>(model, tuning);
    }

    /**
     * Grid-search the best hyperparameters, then retrain on the full dataset with those params.
     * Returns both the trained model and the tuning metadata.
     *
     * @param dataset       full training dataset
     * @param positiveLabel name of the positive class (e.g. "CHURNED", "MATCH"); null = macro average
     * @return trained model plus tuning metadata
     */
    public TunedModel<Label> tuneAndTrainClassifier(Dataset<Label> dataset, String positiveLabel) {
        TuningResult tuning = tuneClassifier(dataset, positiveLabel);
        XGBoostClassificationTrainer trainer =
                new XGBoostClassificationTrainer(tuning.bestNumRounds(), tuning.bestParams());
        Model<Label> model = trainer.train(dataset);
        log.info("[HyperparamTuner] Final classifier trained: rounds={} eta={} maxDepth={}",
                tuning.bestNumRounds(), tuning.bestEta(), tuning.bestMaxDepth());
        return new TunedModel<>(model, tuning);
    }

    // ── Regression tuning ────────────────────────────────────────────────────

    /**
     * Grid-search the best numRounds / eta / maxDepth for a regression task.
     * Metric: RMSE (minimised via k-fold cross-validation).
     *
     * @param dataset labelled regression dataset (typically the training split)
     * @return best hyperparameter combination found
     */
    public TuningResult tuneRegressor(Dataset<Regressor> dataset) {
        log.info("[HyperparamTuner] Tuning regressor on {} examples ({}-fold CV, {} combos)",
                dataset.size(), K_FOLDS, ROUNDS_GRID.length * ETA_GRID.length * DEPTH_GRID.length);

        double bestScore  = Double.MAX_VALUE;
        int    bestRounds = ROUNDS_GRID[0];
        double bestEta    = ETA_GRID[0];
        int    bestDepth  = DEPTH_GRID[0];

        List<MutableDataset<Regressor>> folds = splitRegressionFolds(dataset, K_FOLDS);

        for (int rounds : ROUNDS_GRID) {
            for (double eta : ETA_GRID) {
                for (int depth : DEPTH_GRID) {
                    Map<String, Object> params = buildParams(eta, depth);
                    double cvRmse = crossValidateRegressor(folds, rounds, params);
                    log.debug("[HyperparamTuner] rounds={} eta={} depth={} → cvRMSE={}",
                            rounds, eta, depth, String.format("%.4f", cvRmse));
                    if (cvRmse < bestScore) {
                        bestScore  = cvRmse;
                        bestRounds = rounds;
                        bestEta    = eta;
                        bestDepth  = depth;
                    }
                }
            }
        }

        Map<String, Object> bestParams = buildParams(bestEta, bestDepth);
        log.info("[HyperparamTuner] Best regression config: rounds={} eta={} maxDepth={} cvRMSE={}",
                bestRounds, bestEta, bestDepth, String.format("%.4f", bestScore));
        return new TuningResult(bestRounds, bestEta, bestDepth, bestScore, "rmse", bestParams);
    }

    // ── Classification tuning ────────────────────────────────────────────────

    /**
     * Grid-search the best numRounds / eta / maxDepth for a binary classification task.
     * Metric: AUC-ROC proxy (precision + recall) / 2 (maximised via k-fold cross-validation).
     *
     * @param dataset       labelled classification dataset (typically the training split)
     * @param positiveLabel name of the positive class (e.g. "CHURNED", "MATCH"); null = macro average
     * @return best hyperparameter combination found
     */
    public TuningResult tuneClassifier(Dataset<Label> dataset, String positiveLabel) {
        log.info("[HyperparamTuner] Tuning classifier on {} examples ({}-fold CV, {} combos)",
                dataset.size(), K_FOLDS, ROUNDS_GRID.length * ETA_GRID.length * DEPTH_GRID.length);

        double bestScore  = -1.0;
        int    bestRounds = ROUNDS_GRID[0];
        double bestEta    = ETA_GRID[0];
        int    bestDepth  = DEPTH_GRID[0];

        List<MutableDataset<Label>> folds = splitClassificationFolds(dataset, K_FOLDS);

        for (int rounds : ROUNDS_GRID) {
            for (double eta : ETA_GRID) {
                for (int depth : DEPTH_GRID) {
                    Map<String, Object> params = buildParams(eta, depth);
                    double cvAuc = crossValidateClassifier(folds, rounds, params, positiveLabel);
                    log.debug("[HyperparamTuner] rounds={} eta={} depth={} → cvAUC={}",
                            rounds, eta, depth, String.format("%.4f", cvAuc));
                    if (cvAuc > bestScore) {
                        bestScore  = cvAuc;
                        bestRounds = rounds;
                        bestEta    = eta;
                        bestDepth  = depth;
                    }
                }
            }
        }

        Map<String, Object> bestParams = buildParams(bestEta, bestDepth);
        log.info("[HyperparamTuner] Best classification config: rounds={} eta={} maxDepth={} cvAUC={}",
                bestRounds, bestEta, bestDepth, String.format("%.4f", bestScore));
        return new TuningResult(bestRounds, bestEta, bestDepth, bestScore, "auc_roc", bestParams);
    }

    // ── Cross-validation helpers ─────────────────────────────────────────────

    private double crossValidateRegressor(List<MutableDataset<Regressor>> folds,
                                          int rounds,
                                          Map<String, Object> params) {
        RegressionEvaluator evaluator = new RegressionEvaluator();
        double total = 0.0;
        int    count = 0;

        for (int i = 0; i < folds.size(); i++) {
            MutableDataset<Regressor> testFold  = folds.get(i);
            MutableDataset<Regressor> trainFold = mergeRegressionFolds(folds, i);
            if (trainFold.size() == 0 || testFold.size() == 0) continue;

            try {
                XGBoostRegressionTrainer trainer = new XGBoostRegressionTrainer(
                        XGBoostRegressionTrainer.RegressionType.LINEAR, rounds, params);
                Model<Regressor> model = trainer.train(trainFold);
                RegressionEvaluation eval = evaluator.evaluate(model, testFold);
                double rmse = eval.rmse().values().iterator().next();
                if (Double.isFinite(rmse)) {
                    total += rmse;
                    count++;
                }
            } catch (Exception e) {
                log.debug("[HyperparamTuner] Regression CV fold {} failed: {}", i, e.getMessage());
            }
        }

        return count > 0 ? total / count : Double.MAX_VALUE;
    }

    private double crossValidateClassifier(List<MutableDataset<Label>> folds,
                                           int rounds,
                                           Map<String, Object> params,
                                           String positiveLabel) {
        LabelEvaluator evaluator = new LabelEvaluator();
        double total = 0.0;
        int    count = 0;

        for (int i = 0; i < folds.size(); i++) {
            MutableDataset<Label> testFold  = folds.get(i);
            MutableDataset<Label> trainFold = mergeClassificationFolds(folds, i);
            if (trainFold.size() < 2 || testFold.size() == 0) continue;

            // XGBoost requires at least 2 distinct classes in training data
            long distinctClasses = countDistinctLabels(trainFold);
            if (distinctClasses < 2) continue;

            try {
                XGBoostClassificationTrainer trainer =
                        new XGBoostClassificationTrainer(rounds, params);
                Model<Label> model = trainer.train(trainFold);
                LabelEvaluation eval = evaluator.evaluate(model, testFold);

                double precision, recall;
                if (positiveLabel != null) {
                    Label pos = new Label(positiveLabel);
                    precision = eval.precision(pos);
                    recall    = eval.recall(pos);
                } else {
                    precision = eval.macroAveragedPrecision();
                    recall    = eval.macroAveragedRecall();
                }

                double auc = (precision + recall) / 2.0;
                if (Double.isFinite(auc)) {
                    total += auc;
                    count++;
                }
            } catch (Exception e) {
                log.debug("[HyperparamTuner] Classification CV fold {} failed: {}", i, e.getMessage());
            }
        }

        return count > 0 ? total / count : 0.0;
    }

    // ── Dataset splitting helpers ────────────────────────────────────────────

    private List<MutableDataset<Regressor>> splitRegressionFolds(Dataset<Regressor> dataset, int k) {
        RegressionFactory factory = new RegressionFactory();
        List<MutableDataset<Regressor>> folds = new ArrayList<>(k);
        for (int i = 0; i < k; i++) {
            folds.add(new MutableDataset<>(
                    new SimpleDataSourceProvenance("cvFold_r_" + i, factory), factory));
        }
        for (int i = 0; i < dataset.size(); i++) {
            folds.get(i % k).add(dataset.getExample(i));
        }
        return folds;
    }

    private List<MutableDataset<Label>> splitClassificationFolds(Dataset<Label> dataset, int k) {
        LabelFactory factory = new LabelFactory();
        List<MutableDataset<Label>> folds = new ArrayList<>(k);
        for (int i = 0; i < k; i++) {
            folds.add(new MutableDataset<>(
                    new SimpleDataSourceProvenance("cvFold_c_" + i, factory), factory));
        }
        for (int i = 0; i < dataset.size(); i++) {
            folds.get(i % k).add(dataset.getExample(i));
        }
        return folds;
    }

    private MutableDataset<Regressor> mergeRegressionFolds(List<MutableDataset<Regressor>> folds,
                                                            int excludeIdx) {
        RegressionFactory factory = new RegressionFactory();
        MutableDataset<Regressor> merged = new MutableDataset<>(
                new SimpleDataSourceProvenance("cvTrain_r", factory), factory);
        for (int i = 0; i < folds.size(); i++) {
            if (i == excludeIdx) continue;
            for (int j = 0; j < folds.get(i).size(); j++) {
                merged.add(folds.get(i).getExample(j));
            }
        }
        return merged;
    }

    private MutableDataset<Label> mergeClassificationFolds(List<MutableDataset<Label>> folds,
                                                           int excludeIdx) {
        LabelFactory factory = new LabelFactory();
        MutableDataset<Label> merged = new MutableDataset<>(
                new SimpleDataSourceProvenance("cvTrain_c", factory), factory);
        for (int i = 0; i < folds.size(); i++) {
            if (i == excludeIdx) continue;
            for (int j = 0; j < folds.get(i).size(); j++) {
                merged.add(folds.get(i).getExample(j));
            }
        }
        return merged;
    }

    private long countDistinctLabels(MutableDataset<Label> dataset) {
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < dataset.size(); i++) {
            seen.add(dataset.getExample(i).getOutput().getLabel());
        }
        return seen.size();
    }

    private Map<String, Object> buildParams(double eta, int maxDepth) {
        Map<String, Object> params = new HashMap<>();
        params.put("eta", eta);
        params.put("max_depth", maxDepth);
        params.put("subsample", 0.8);
        params.put("colsample_bytree", 0.8);
        return params;
    }

    // ── Result records ───────────────────────────────────────────────────────

    /**
     * Best hyperparameter combination found by grid-search cross-validation.
     *
     * @param bestNumRounds best XGBoost numRounds
     * @param bestEta       best learning rate (eta)
     * @param bestMaxDepth  best tree max depth
     * @param bestScore     best CV score (RMSE for regression, AUC-ROC for classification)
     * @param metric        "rmse" (lower is better) or "auc_roc" (higher is better)
     * @param bestParams    ready-to-use params map for the XGBoost Map-based constructor
     */
    public record TuningResult(
            int                 bestNumRounds,
            double              bestEta,
            int                 bestMaxDepth,
            double              bestScore,
            String              metric,
            Map<String, Object> bestParams
    ) {}

    /**
     * Result of {@link #tuneAndTrainRegressor} / {@link #tuneAndTrainClassifier}.
     *
     * @param model  trained model (ready for evaluation and registration)
     * @param tuning hyperparameter combination used to train the model
     */
    public record TunedModel<T extends org.tribuo.Output<T>>(
            Model<T>      model,
            TuningResult  tuning
    ) {}
}
