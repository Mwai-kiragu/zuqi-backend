package com.zuqi.ai.model.tuning;

import com.zuqi.ai.model.ModelRegistry;
import com.zuqi.ai.synthetic.DataMixer;
import com.zuqi.ai.synthetic.DataPhaseTracker;
import com.zuqi.ai.synthetic.SyntheticDataBundle;
import com.zuqi.ai.synthetic.SyntheticDataConfig;
import com.zuqi.ai.synthetic.SyntheticDataOrchestrator;
import com.zuqi.ai.synthetic.SyntheticFeatureStore;
import com.zuqi.domain.ai.AIModelRegistry;
import com.zuqi.domain.ai.DataPhase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.tribuo.Example;
import org.tribuo.Model;
import org.tribuo.MutableDataset;
import org.tribuo.Trainer;
import org.tribuo.anomaly.AnomalyFactory;
import org.tribuo.anomaly.Event;
import org.tribuo.classification.Label;
import org.tribuo.classification.LabelFactory;
import org.tribuo.clustering.ClusterID;
import org.tribuo.provenance.SimpleDataSourceProvenance;
import org.tribuo.regression.Regressor;
import org.tribuo.regression.RegressionFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Orchestrates hyperparameter tuning for all 15 Zuqi ML models.
 *
 * <h3>Algorithm per model</h3>
 * <ol>
 *   <li>Generate a deterministic synthetic bundle.</li>
 *   <li>Build Tribuo training examples via {@link SyntheticFeatureStore}.</li>
 *   <li>Split examples 80 / 20 into a training partition and a held-out validation set.</li>
 *   <li>Run k-fold CV over the {@link HyperparameterGrid} on the <em>training partition only</em>.</li>
 *   <li>Re-train a final model on the training partition with the best hyperparameters.</li>
 *   <li>Evaluate the final model on the holdout set via {@link HoldoutValidator}.</li>
 *   <li>If the holdout metric clears the threshold, register, stamp data-phase metadata,
 *       and promote to ACTIVE; otherwise log a warning and skip promotion.</li>
 *   <li>Persist best hyperparameters via {@link ModelRegistry#updateHyperparameters}.</li>
 * </ol>
 *
 * <h3>Holdout thresholds</h3>
 * <ul>
 *   <li>Classifiers:       macro-F1 ≥ {@value HoldoutValidator#MIN_CLASSIFIER_F1}</li>
 *   <li>Regressors:        normalised RMSE ≤ {@value HoldoutValidator#MAX_REGRESSOR_NRMSE}</li>
 *   <li>Anomaly detectors: anomaly F1 ≥ {@value HoldoutValidator#MIN_ANOMALY_F1}</li>
 *   <li>K-Means (unsupervised): no holdout gate — always promoted.</li>
 * </ul>
 *
 * <h3>Failure isolation</h3>
 * Tuning failures for individual models are caught, logged, and collected in the
 * {@link TuningRunResult} — they do not abort tuning for the remaining models.
 * Holdout failures are also collected as errors so the operator is alerted and the
 * previous ACTIVE version remains live.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ModelTuningService {

    private final SyntheticDataOrchestrator orchestrator;
    private final SyntheticFeatureStore     featureStore;
    private final DataMixer                 dataMixer;
    private final ModelRegistry             modelRegistry;
    private final DataPhaseTracker          phaseTracker;
    private final CrossValidationTuner      cvTuner;
    private final HoldoutValidator          holdoutValidator;
    private final Trainer<ClusterID>        kMeansTrainer;

    static final int MIN_CLASSIFICATION_EXAMPLES = 10;
    static final int MIN_REGRESSION_EXAMPLES     = 10;
    static final int MIN_ANOMALY_EXAMPLES        = 5;

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Run hyperparameter tuning for all 15 trainable models for the given distributor.
     *
     * @param distributorId distributor scope
     * @param config        synthetic data generation config (defines bundle size / seed)
     * @return summary of tuning results and any per-model errors
     */
    public TuningRunResult tuneAllModels(UUID distributorId, SyntheticDataConfig config) {
        return tuneAllModels(distributorId, config, Set.of());
    }

    /**
     * Run hyperparameter tuning for a filtered subset of models.
     *
     * @param distributorId distributor scope
     * @param config        synthetic data generation config
     * @param modelFilter   if non-empty, only tune models whose name is in this set;
     *                      empty set means tune all models
     * @return summary of tuning results and any per-model errors
     */
    public TuningRunResult tuneAllModels(UUID distributorId, SyntheticDataConfig config,
                                          Set<String> modelFilter) {
        long startMs = System.currentTimeMillis();
        if (modelFilter.isEmpty()) {
            log.info("[Tuning] Starting hyperparameter tuning — distributor={} (all models)", distributorId);
        } else {
            log.info("[Tuning] Starting hyperparameter tuning — distributor={} models={}", distributorId, modelFilter);
        }

        SyntheticDataBundle bundle = orchestrator.generateBundle(config);
        log.info("[Tuning] Bundle generated — {} merchants", bundle.getMerchants().size());

        List<TuningResult> results = new ArrayList<>();
        List<String>       errors  = new ArrayList<>();

        List<CandidateConfig<Label>>     classifCandidates = HyperparameterGrid.classificationCandidates();
        List<CandidateConfig<Regressor>> regCandidates     = HyperparameterGrid.regressionCandidates();
        List<CandidateConfig<Event>>     anomalyCandidates = HyperparameterGrid.anomalyCandidates();

        // ── classifiers ───────────────────────────────────────────────────
        if (shouldTune(DataPhaseTracker.MODEL_CREDIT_CLASSIFIER, modelFilter))
            tuneClassifier(DataPhaseTracker.MODEL_CREDIT_CLASSIFIER,
                    b -> featureStore.buildCreditClassifierExamples(b),
                    bundle, classifCandidates, distributorId, results, errors);

        if (shouldTune(DataPhaseTracker.MODEL_STOCKOUT_PREDICTOR, modelFilter))
            tuneClassifier(DataPhaseTracker.MODEL_STOCKOUT_PREDICTOR,
                    b -> featureStore.buildStockoutPredictorExamples(b),
                    bundle, classifCandidates, distributorId, results, errors);

        if (shouldTune(DataPhaseTracker.MODEL_REP_PERFORMANCE_PREDICTOR, modelFilter))
            tuneClassifier(DataPhaseTracker.MODEL_REP_PERFORMANCE_PREDICTOR,
                    b -> featureStore.buildRepPerformancePredictorExamples(b),
                    bundle, classifCandidates, distributorId, results, errors);

        if (shouldTune(DataPhaseTracker.MODEL_PAYMENT_DISTRESS_CLASSIFIER, modelFilter))
            tuneClassifier(DataPhaseTracker.MODEL_PAYMENT_DISTRESS_CLASSIFIER,
                    b -> featureStore.buildPaymentDistressExamples(b),
                    bundle, classifCandidates, distributorId, results, errors);

        if (shouldTune(DataPhaseTracker.MODEL_DATA_QUALITY_DETECTOR, modelFilter))
            tuneClassifier(DataPhaseTracker.MODEL_DATA_QUALITY_DETECTOR,
                    b -> featureStore.buildDataQualityExamples(b),
                    bundle, classifCandidates, distributorId, results, errors);

        // ── regressors ────────────────────────────────────────────────────
        if (shouldTune(DataPhaseTracker.MODEL_CREDIT_LIMIT_REGRESSOR, modelFilter))
            tuneRegressor(DataPhaseTracker.MODEL_CREDIT_LIMIT_REGRESSOR,
                    b -> featureStore.buildCreditLimitRegressorExamples(b),
                    bundle, regCandidates, distributorId, results, errors);

        if (shouldTune(DataPhaseTracker.MODEL_DEMAND_FORECASTER, modelFilter))
            tuneRegressor(DataPhaseTracker.MODEL_DEMAND_FORECASTER,
                    b -> featureStore.buildDemandForecasterExamples(b),
                    bundle, regCandidates, distributorId, results, errors);

        // ── anomaly detectors ─────────────────────────────────────────────
        if (shouldTune(DataPhaseTracker.MODEL_SHRINKAGE_DETECTOR, modelFilter))
            tuneAnomalyDetector(DataPhaseTracker.MODEL_SHRINKAGE_DETECTOR,
                    b -> featureStore.buildShrinkageDetectorExamples(b),
                    bundle, anomalyCandidates, distributorId, results, errors);

        if (shouldTune(DataPhaseTracker.MODEL_PAYMENT_ANOMALY_DETECTOR, modelFilter))
            tuneAnomalyDetector(DataPhaseTracker.MODEL_PAYMENT_ANOMALY_DETECTOR,
                    b -> featureStore.buildPaymentAnomalyExamples(b),
                    bundle, anomalyCandidates, distributorId, results, errors);

        // ── Phase 7 classifiers ───────────────────────────────────────────
        if (shouldTune(DataPhaseTracker.MODEL_BANK_RECON_MATCHER, modelFilter))
            tuneClassifier(DataPhaseTracker.MODEL_BANK_RECON_MATCHER,
                    b -> featureStore.buildBankReconExamples(b),
                    bundle, classifCandidates, distributorId, results, errors);

        if (shouldTune(DataPhaseTracker.MODEL_CHURN_PREDICTOR, modelFilter))
            tuneClassifier(DataPhaseTracker.MODEL_CHURN_PREDICTOR,
                    b -> featureStore.buildChurnExamples(b),
                    bundle, classifCandidates, distributorId, results, errors);

        if (shouldTune(DataPhaseTracker.MODEL_EXPIRY_RISK_PREDICTOR, modelFilter))
            tuneClassifier(DataPhaseTracker.MODEL_EXPIRY_RISK_PREDICTOR,
                    b -> featureStore.buildExpiryRiskExamples(b),
                    bundle, classifCandidates, distributorId, results, errors);

        // ── Phase 7 regressors ────────────────────────────────────────────
        if (shouldTune(DataPhaseTracker.MODEL_CASH_FLOW_PREDICTOR, modelFilter))
            tuneRegressor(DataPhaseTracker.MODEL_CASH_FLOW_PREDICTOR,
                    b -> featureStore.buildCashFlowExamples(b),
                    bundle, regCandidates, distributorId, results, errors);

        if (shouldTune(DataPhaseTracker.MODEL_CUSTOMER_CLV_PREDICTOR, modelFilter))
            tuneRegressor(DataPhaseTracker.MODEL_CUSTOMER_CLV_PREDICTOR,
                    b -> featureStore.buildClvExamples(b),
                    bundle, regCandidates, distributorId, results, errors);

        // ── K-Means (unsupervised — no CV, train once with configured k) ──
        if (shouldTune(DataPhaseTracker.MODEL_CUSTOMER_SEGMENTER, modelFilter))
            trainKMeans(bundle, distributorId, results, errors);

        long durationMs = System.currentTimeMillis() - startMs;
        log.info("[Tuning] Complete — tuned={}, errors={}, duration={}ms",
                results.size(), errors.size(), durationMs);

        return new TuningRunResult(results, errors, errors.isEmpty(), durationMs);
    }

    // ── Private helpers ───────────────────────────────────────────────────

    /** Returns true when the model should be tuned given the current filter. */
    private boolean shouldTune(String modelName, Set<String> filter) {
        return filter.isEmpty() || filter.contains(modelName);
    }

    private void tuneClassifier(String modelName,
                                 Function<SyntheticDataBundle, List<Example<Label>>> exampleFn,
                                 SyntheticDataBundle bundle,
                                 List<CandidateConfig<Label>> candidates,
                                 UUID distributorId,
                                 List<TuningResult> results,
                                 List<String> errors) {
        try {
            List<Example<Label>> examples = exampleFn.apply(bundle);
            List<Example<Label>> mixed = dataMixer.buildTrainingDataset(
                    modelName, distributorId, List.of(), examples);

            if (mixed.size() < MIN_CLASSIFICATION_EXAMPLES) {
                log.warn("[Tuning] {} — too few examples ({}), skipping", modelName, mixed.size());
                return;
            }
            if (isSingleClass(mixed)) {
                log.warn("[Tuning] {} — single-class dataset, skipping", modelName);
                return;
            }

            // Oversample minority class so XGBoost doesn't ignore it
            List<Example<Label>> balanced = oversampleMinorityClass(mixed, modelName);

            // Split 80/20 before CV — holdout is never seen during tuning or training
            HoldoutValidator.HoldoutSplit<Example<Label>> split = holdoutValidator.split(balanced);
            MutableDataset<Label> trainDataset = toClassificationDataset(split.train(), modelName);

            CrossValidationTuner.BestConfig<Label> best = cvTuner.tuneClassifier(
                    modelName, candidates, trainDataset);

            Model<Label> finalModel = best.config().trainer().train(trainDataset);

            ValidationResult holdout = holdoutValidator.validateClassifier(
                    finalModel, split.holdout(), modelName);
            if (!holdout.passed()) {
                log.warn("[Tuning] {} — holdout FAILED ({} = {} < threshold {}), skipping promotion",
                        modelName, holdout.metricName(), holdout.holdoutValue(), holdout.threshold());
                errors.add(modelName + ": holdout validation failed ("
                        + holdout.metricName() + "=" + holdout.holdoutValue()
                        + " < " + holdout.threshold() + ")");
                return;
            }

            UUID modelId = registerAndPromote(modelName, "xgboost_classification",
                    finalModel, best.config().hyperparameters(),
                    mixed.size(), 0, distributorId, best.metricValue(), best.metricName(), holdout);

            results.add(new TuningResult(modelName, modelId,
                    best.config().hyperparameters(),
                    best.metricValue(), best.metricName(),
                    best.candidatesEvaluated(), best.numFolds(),
                    holdout.metricName(), holdout.holdoutValue(), holdout.passed()));

        } catch (Exception e) {
            log.error("[Tuning] {} failed: {}", modelName, e.getMessage(), e);
            errors.add(modelName + ": " + e.getMessage());
        }
    }

    private void tuneRegressor(String modelName,
                                Function<SyntheticDataBundle, List<Example<Regressor>>> exampleFn,
                                SyntheticDataBundle bundle,
                                List<CandidateConfig<Regressor>> candidates,
                                UUID distributorId,
                                List<TuningResult> results,
                                List<String> errors) {
        try {
            List<Example<Regressor>> examples = exampleFn.apply(bundle);
            List<Example<Regressor>> mixed = dataMixer.buildTrainingDataset(
                    modelName, distributorId, List.of(), examples);

            if (mixed.size() < MIN_REGRESSION_EXAMPLES) {
                log.warn("[Tuning] {} — too few examples ({}), skipping", modelName, mixed.size());
                return;
            }

            // Split 80/20 before CV — holdout is never seen during tuning or training
            HoldoutValidator.HoldoutSplit<Example<Regressor>> split = holdoutValidator.split(mixed);
            MutableDataset<Regressor> trainDataset = toRegressionDataset(split.train(), modelName);

            CrossValidationTuner.BestConfig<Regressor> best = cvTuner.tuneRegressor(
                    modelName, candidates, trainDataset);

            Model<Regressor> finalModel = best.config().trainer().train(trainDataset);

            ValidationResult holdout = holdoutValidator.validateRegressor(
                    finalModel, split.holdout(), modelName);
            if (!holdout.passed()) {
                log.warn("[Tuning] {} — holdout FAILED ({} = {} > threshold {}), skipping promotion",
                        modelName, holdout.metricName(), holdout.holdoutValue(), holdout.threshold());
                errors.add(modelName + ": holdout validation failed ("
                        + holdout.metricName() + "=" + holdout.holdoutValue()
                        + " > " + holdout.threshold() + ")");
                return;
            }

            UUID modelId = registerAndPromote(modelName, "xgboost_regression",
                    finalModel, best.config().hyperparameters(),
                    mixed.size(), 0, distributorId, best.metricValue(), best.metricName(), holdout);

            results.add(new TuningResult(modelName, modelId,
                    best.config().hyperparameters(),
                    best.metricValue(), best.metricName(),
                    best.candidatesEvaluated(), best.numFolds(),
                    holdout.metricName(), holdout.holdoutValue(), holdout.passed()));

        } catch (Exception e) {
            log.error("[Tuning] {} failed: {}", modelName, e.getMessage(), e);
            errors.add(modelName + ": " + e.getMessage());
        }
    }

    private void tuneAnomalyDetector(String modelName,
                                      Function<SyntheticDataBundle, List<Example<Event>>> exampleFn,
                                      SyntheticDataBundle bundle,
                                      List<CandidateConfig<Event>> candidates,
                                      UUID distributorId,
                                      List<TuningResult> results,
                                      List<String> errors) {
        try {
            List<Example<Event>> examples = exampleFn.apply(bundle);
            List<Example<Event>> mixed = dataMixer.buildTrainingDataset(
                    modelName, distributorId, List.of(), examples);

            if (mixed.size() < MIN_ANOMALY_EXAMPLES) {
                log.warn("[Tuning] {} — too few examples ({}), skipping", modelName, mixed.size());
                return;
            }

            // Split 80/20 before CV — holdout is never seen during tuning or training.
            // LibSVMAnomalyTrainer (one-class SVM) only accepts EXPECTED events at training
            // time. CV tuner receives ALL examples from the train split so test folds contain
            // ANOMALOUS events for meaningful F1 evaluation. The final model is trained on
            // EXPECTED-only examples from the train split.
            HoldoutValidator.HoldoutSplit<Example<Event>> split = holdoutValidator.split(mixed);

            List<Example<Event>> trainExpected = split.train().stream()
                    .filter(ex -> ex.getOutput().getType() == Event.EventType.EXPECTED)
                    .collect(Collectors.toList());

            if (trainExpected.size() < MIN_ANOMALY_EXAMPLES) {
                log.warn("[Tuning] {} — too few EXPECTED examples in train split ({} total, {} expected), skipping",
                        modelName, mixed.size(), trainExpected.size());
                return;
            }

            // Pass the full train split (EXPECTED + ANOMALOUS) to CV for F1 evaluation
            CrossValidationTuner.BestConfig<Event> best = cvTuner.tuneAnomalyDetector(
                    modelName, candidates, split.train());

            // Final model trained on EXPECTED-only (LibSVM requirement)
            MutableDataset<Event> finalTrainDataset = toAnomalyDataset(trainExpected, modelName + "_tuning");
            Model<Event> finalModel = best.config().trainer().train(finalTrainDataset);

            // Holdout set contains EXPECTED + ANOMALOUS for meaningful F1 evaluation
            ValidationResult holdout = holdoutValidator.validateAnomalyDetector(
                    finalModel, split.holdout(), modelName);
            if (!holdout.passed()) {
                log.warn("[Tuning] {} — holdout FAILED ({} = {} < threshold {}), skipping promotion",
                        modelName, holdout.metricName(), holdout.holdoutValue(), holdout.threshold());
                errors.add(modelName + ": holdout validation failed ("
                        + holdout.metricName() + "=" + holdout.holdoutValue()
                        + " < " + holdout.threshold() + ")");
                return;
            }

            UUID modelId = registerAndPromote(modelName, "libsvm_anomaly",
                    finalModel, best.config().hyperparameters(),
                    mixed.size(), 0, distributorId, best.metricValue(), best.metricName(), holdout);

            results.add(new TuningResult(modelName, modelId,
                    best.config().hyperparameters(),
                    best.metricValue(), best.metricName(),
                    best.candidatesEvaluated(), best.numFolds(),
                    holdout.metricName(), holdout.holdoutValue(), holdout.passed()));

        } catch (Exception e) {
            log.error("[Tuning] {} failed: {}", modelName, e.getMessage(), e);
            errors.add(modelName + ": " + e.getMessage());
        }
    }

    // ── Dataset builders ──────────────────────────────────────────────────

    private MutableDataset<Label> toClassificationDataset(
            List<Example<Label>> examples, String modelName) {
        SimpleDataSourceProvenance prov = new SimpleDataSourceProvenance(
                modelName + "_tuning", new LabelFactory());
        MutableDataset<Label> ds = new MutableDataset<>(prov, new LabelFactory());
        examples.forEach(ds::add);
        return ds;
    }

    private MutableDataset<Regressor> toRegressionDataset(
            List<Example<Regressor>> examples, String modelName) {
        SimpleDataSourceProvenance prov = new SimpleDataSourceProvenance(
                modelName + "_tuning", new RegressionFactory());
        MutableDataset<Regressor> ds = new MutableDataset<>(prov, new RegressionFactory());
        examples.forEach(ds::add);
        return ds;
    }

    private MutableDataset<Event> toAnomalyDataset(
            List<Example<Event>> examples, String provenance) {
        AnomalyFactory factory = new AnomalyFactory();
        SimpleDataSourceProvenance prov = new SimpleDataSourceProvenance(provenance, factory);
        MutableDataset<Event> ds = new MutableDataset<>(prov, factory);
        examples.forEach(ds::add);
        return ds;
    }

    // ── Registry helpers ──────────────────────────────────────────────────

    private UUID registerAndPromote(String modelName, String algorithm,
                                     Model<?> model,
                                     Map<String, Object> rawHyperparameters,
                                     int syntheticCount, int realCount,
                                     UUID distributorId,
                                     double metricValue, String metricName,
                                     ValidationResult holdout) {

        Map<String, Object> hparams = new LinkedHashMap<>(rawHyperparameters);
        hparams.put("tuning_metric",       metricName);
        hparams.put("tuning_metric_value", metricValue);
        hparams.put("training_phase",      "SYNTHETIC");
        hparams.put("algorithm",           algorithm);

        AIModelRegistry entry = modelRegistry.registerModel(
                modelName, algorithm, hparams, "hyperparameter_tuner");

        int featureCount = safeFeatureCount(model);
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("training_phase",    "SYNTHETIC");
        metrics.put("synthetic_records", syntheticCount);
        metrics.put("real_records",      realCount);
        metrics.put("feature_count",     featureCount);
        metrics.put(metricName,          metricValue);
        if (holdout != null && !holdout.wasSkipped()) {
            metrics.put("holdout_" + holdout.metricName(), holdout.holdoutValue());
            metrics.put("holdout_threshold",               holdout.threshold());
            metrics.put("holdout_passed",                  holdout.passed());
        }

        modelRegistry.updateModelAfterTraining(
                entry.getId(), metrics, serialize(model), Map.of("feature_count", featureCount));
        modelRegistry.setDataPhaseMetadata(
                entry.getId(), DataPhase.SYNTHETIC, syntheticCount, realCount);
        modelRegistry.promoteToActive(entry.getId());
        modelRegistry.updateHyperparameters(entry.getId(), hparams);

        if (holdout != null && !holdout.wasSkipped()) {
            log.info("[Tuning] Registered and promoted {} ({}={}, holdout_{}={}, id={})",
                    modelName, metricName, metricValue,
                    holdout.metricName(), holdout.holdoutValue(), entry.getId());
        } else {
            log.info("[Tuning] Registered and promoted {} ({}={}, id={})",
                    modelName, metricName, metricValue, entry.getId());
        }
        return entry.getId();
    }

    private int safeFeatureCount(Model<?> model) {
        try { return model.getFeatureIDMap().size(); } catch (Exception e) { return 0; }
    }

    /**
     * Oversample the minority class by random repetition until the majority:minority
     * ratio is at most 3:1. This prevents XGBoost from ignoring rare classes entirely.
     * No oversampling is applied when the dataset is already balanced (ratio ≤ 3).
     */
    private List<Example<Label>> oversampleMinorityClass(List<Example<Label>> examples, String modelName) {
        Map<String, List<Example<Label>>> byClass = examples.stream()
                .collect(Collectors.groupingBy(ex -> ex.getOutput().getLabel()));

        if (byClass.size() != 2) return examples; // only for binary classifiers

        List<Example<Label>> majority = byClass.values().stream()
                .max((a, b) -> Integer.compare(a.size(), b.size())).orElse(List.of());
        List<Example<Label>> minority = byClass.values().stream()
                .min((a, b) -> Integer.compare(a.size(), b.size())).orElse(List.of());

        double ratio = (double) majority.size() / minority.size();
        if (ratio <= 3.0) return examples;

        int targetSize = majority.size() / 3; // target: 3:1 majority:minority
        int toAdd      = Math.max(0, targetSize - minority.size());

        log.info("[Tuning] {} — class imbalance ratio={}, oversampling minority '{}' by {} examples",
                modelName, String.format("%.1f", ratio), minority.get(0).getOutput().getLabel(), toAdd);

        List<Example<Label>> result = new ArrayList<>(examples);
        Random rng = new Random(42L);
        for (int i = 0; i < toAdd; i++) {
            result.add(minority.get(rng.nextInt(minority.size())));
        }
        Collections.shuffle(result, rng);
        return result;
    }

    private boolean isSingleClass(List<Example<Label>> examples) {
        if (examples.isEmpty()) return true;
        return examples.stream()
                .map(ex -> ex.getOutput().getLabel())
                .distinct().count() <= 1;
    }

    private byte[] serialize(Model<?> model) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(model);
            oos.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Model serialization failed for " + model.getName(), e);
        }
    }

    // ── K-Means ───────────────────────────────────────────────────────────

    /**
     * Train a K-Means segmentation model and register it as ACTIVE.
     *
     * <p>K-Means is unsupervised — there are no hyperparameter candidates to compare
     * via cross-validation. A single model is trained using the configured cluster count
     * (default k=5 from {@code zuqi.ai.kmeans.clusters}) and immediately promoted.
     *
     * <p>Metric recorded is {@code num_examples} (training set size) since no
     * supervised quality metric applies.
     */
    private void trainKMeans(SyntheticDataBundle bundle, UUID distributorId,
                              List<TuningResult> results, List<String> errors) {
        String modelName = DataPhaseTracker.MODEL_CUSTOMER_SEGMENTER;
        try {
            MutableDataset<ClusterID> dataset = featureStore.buildSegmentationDataset(bundle);
            if (dataset.size() < 10) {
                log.warn("[Tuning] {} — too few examples ({}), skipping", modelName, dataset.size());
                return;
            }

            Model<ClusterID> model = kMeansTrainer.train(dataset);

            Map<String, Object> hparams = new LinkedHashMap<>();
            hparams.put("clusters", 5);

            UUID modelId = registerAndPromote(modelName, "kmeans",
                    model, hparams, dataset.size(), 0,
                    distributorId, (double) dataset.size(), "num_examples", null);

            results.add(new TuningResult(modelName, modelId, hparams,
                    dataset.size(), "num_examples", 1, 0));

            log.info("[Tuning] {} — K-Means trained on {} examples", modelName, dataset.size());

        } catch (Exception e) {
            log.error("[Tuning] {} failed: {}", modelName, e.getMessage(), e);
            errors.add(modelName + ": " + e.getMessage());
        }
    }

    // ── Result type ───────────────────────────────────────────────────────

    /**
     * Immutable summary of a full tuning run across all models.
     *
     * @param results    per-model tuning results
     * @param errors     per-model error messages (empty when fully successful)
     * @param success    true when all models tuned without errors
     * @param durationMs wall-clock time of the entire run
     */
    public record TuningRunResult(
            List<TuningResult> results,
            List<String>       errors,
            boolean            success,
            long               durationMs) {}
}
