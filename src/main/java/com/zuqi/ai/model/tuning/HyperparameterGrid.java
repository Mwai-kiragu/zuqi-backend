package com.zuqi.ai.model.tuning;

import org.tribuo.anomaly.Event;
import org.tribuo.anomaly.libsvm.LibSVMAnomalyTrainer;
import org.tribuo.anomaly.libsvm.SVMAnomalyType;
import org.tribuo.classification.Label;
import org.tribuo.classification.xgboost.XGBoostClassificationTrainer;
import org.tribuo.common.libsvm.KernelType;
import org.tribuo.common.libsvm.SVMParameters;
import org.tribuo.regression.Regressor;
import org.tribuo.regression.xgboost.XGBoostRegressionTrainer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Static factory that generates candidate {@link CandidateConfig} lists for each
 * model family used in the Zuqi AI layer.
 *
 * <h3>Search strategy</h3>
 * A small, hand-picked grid is used rather than exhaustive or fully random search.
 * The candidates cover the most impactful XGBoost axes (learning rate, depth, rounds)
 * and LibSVM axes (nu, gamma) while keeping tuning runtime under ~5 minutes for
 * 500-merchant synthetic datasets.
 *
 * <h3>Constructor choice</h3>
 * The explicit-parameter XGBoost constructors are used (rather than the
 * {@code Map<String,Object>} override variant) because the Map variant requires
 * an explicit {@code "objective"} key that varies per dataset class count.
 */
public final class HyperparameterGrid {

    // ── Shared XGBoost defaults ─────────────────────────────────────────────
    private static final double GAMMA              = 0.0;
    private static final double MIN_CHILD_WEIGHT   = 1.0;
    private static final double MAX_DELTA_STEP     = 0.0;
    private static final double SUBSAMPLE          = 0.8;
    private static final double FEATURE_SUBSAMPLE  = 0.8;
    private static final double LAMBDA             = 1.0;
    private static final int    NUM_THREADS        = 4;
    private static final boolean SILENT            = true;
    private static final long   SEED               = 42L;

    private HyperparameterGrid() {}

    // ── Classification (Label) ─────────────────────────────────────────────

    /**
     * Returns 5 XGBoost classification candidates varying numRounds, eta, and maxDepth.
     *
     * <p>Constructor used:
     * {@code XGBoostClassificationTrainer(numRounds, eta, gamma, maxDepth,
     * minChildWeight, maxDeltaStep, subsample, featureSubsample, lambda,
     * nThread, silent, seed)}.
     */
    public static List<CandidateConfig<Label>> classificationCandidates() {
        List<CandidateConfig<Label>> candidates = new ArrayList<>();

        // {numRounds, eta×1000, maxDepth}
        int[][] grid = {
                { 50, 300, 6},   // baseline
                {100, 100, 6},   // more rounds, lower lr
                {100, 100, 4},   // more rounds, lower lr, shallower trees
                {200,  50, 6},   // many rounds, very low lr
                {150, 100, 8},   // deeper trees, moderate lr
        };

        for (int[] params : grid) {
            int    numRounds = params[0];
            double eta       = params[1] / 1000.0;
            int    maxDepth  = params[2];

            Map<String, Object> hparams = Map.of(
                    "num_rounds",       numRounds,
                    "eta",              eta,
                    "max_depth",        maxDepth,
                    "subsample",        SUBSAMPLE,
                    "colsample_bytree", FEATURE_SUBSAMPLE
            );

            candidates.add(new CandidateConfig<>(
                    new XGBoostClassificationTrainer(
                            numRounds, eta, GAMMA, maxDepth,
                            MIN_CHILD_WEIGHT, MAX_DELTA_STEP,
                            SUBSAMPLE, FEATURE_SUBSAMPLE, LAMBDA,
                            NUM_THREADS, SILENT, SEED),
                    hparams
            ));
        }

        return candidates;
    }

    // ── Regression (Regressor) ─────────────────────────────────────────────

    /**
     * Returns 5 XGBoost regression candidates varying numRounds, eta, and maxDepth.
     *
     * <p>Constructor used:
     * {@code XGBoostRegressionTrainer(RegressionType.LINEAR, numRounds, eta, gamma,
     * maxDepth, minChildWeight, maxDeltaStep, subsample, featureSubsample, lambda,
     * nThread, silent, seed)}.
     */
    public static List<CandidateConfig<Regressor>> regressionCandidates() {
        List<CandidateConfig<Regressor>> candidates = new ArrayList<>();

        int[][] grid = {
                { 50, 300, 6},
                {100, 100, 6},
                {100, 100, 4},
                {200,  50, 6},
                {150, 100, 8},
        };

        for (int[] params : grid) {
            int    numRounds = params[0];
            double eta       = params[1] / 1000.0;
            int    maxDepth  = params[2];

            Map<String, Object> hparams = Map.of(
                    "num_rounds",       numRounds,
                    "eta",              eta,
                    "max_depth",        maxDepth,
                    "subsample",        SUBSAMPLE,
                    "colsample_bytree", FEATURE_SUBSAMPLE
            );

            candidates.add(new CandidateConfig<>(
                    new XGBoostRegressionTrainer(
                            XGBoostRegressionTrainer.RegressionType.LINEAR,
                            numRounds, eta, GAMMA, maxDepth,
                            MIN_CHILD_WEIGHT, MAX_DELTA_STEP,
                            SUBSAMPLE, FEATURE_SUBSAMPLE, LAMBDA,
                            NUM_THREADS, SILENT, SEED),
                    hparams
            ));
        }

        return candidates;
    }

    // ── Anomaly (Event / LibSVM) ────────────────────────────────────────────

    /**
     * Returns 4 LibSVM one-class candidates varying nu and gamma.
     *
     * <p>nu controls the upper bound on the fraction of anomalies;
     * gamma is the RBF kernel width.
     */
    public static List<CandidateConfig<Event>> anomalyCandidates() {
        List<CandidateConfig<Event>> candidates = new ArrayList<>();

        // {nu×100, gamma×10}
        int[][] grid = {
                { 5, 5},   // nu=0.05, gamma=0.5 — tight boundary
                {10, 5},   // nu=0.10, gamma=0.5 — default
                {10, 1},   // nu=0.10, gamma=0.1 — broader kernel
                {20, 5},   // nu=0.20, gamma=0.5 — expect more anomalies
        };

        for (int[] params : grid) {
            double nu    = params[0] / 100.0;
            double gamma = params[1] / 10.0;

            Map<String, Object> hparams = Map.of(
                    "kernel",   "RBF",
                    "nu",       nu,
                    "gamma",    gamma,
                    "svm_type", "ONE_CLASS"
            );

            SVMParameters<Event> svmParams = new SVMParameters<>(
                    new SVMAnomalyType(SVMAnomalyType.SVMMode.ONE_CLASS), KernelType.RBF);
            svmParams.setNu(nu);
            svmParams.setGamma(gamma);

            candidates.add(new CandidateConfig<>(
                    new LibSVMAnomalyTrainer(svmParams),
                    hparams
            ));
        }

        return candidates;
    }
}
