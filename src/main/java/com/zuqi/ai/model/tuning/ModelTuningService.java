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
import org.tribuo.anomaly.AnomalyFactory;
import org.tribuo.anomaly.Event;
import org.tribuo.classification.Label;
import org.tribuo.classification.LabelFactory;
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
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Orchestrates hyperparameter tuning for all 9 Zuqi ML models.
 *
 * <h3>Algorithm per model</h3>
 * <ol>
 *   <li>Generate a deterministic synthetic bundle.</li>
 *   <li>Build Tribuo training examples via {@link SyntheticFeatureStore}.</li>
 *   <li>Run k-fold CV over the {@link HyperparameterGrid} via {@link CrossValidationTuner}.</li>
 *   <li>Re-train a final model with the best hyperparameters.</li>
 *   <li>Register, stamp data-phase metadata, and promote to ACTIVE.</li>
 *   <li>Persist best hyperparameters via {@link ModelRegistry#updateHyperparameters}.</li>
 * </ol>
 *
 * <h3>Failure isolation</h3>
 * Tuning failures for individual models are caught, logged, and collected in the
 * {@link TuningRunResult} — they do not abort tuning for the remaining models.
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

    static final int MIN_CLASSIFICATION_EXAMPLES = 10;
    static final int MIN_REGRESSION_EXAMPLES     = 10;
    static final int MIN_ANOMALY_EXAMPLES        = 5;

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Run hyperparameter tuning for all 9 models for the given distributor.
     *
     * @param distributorId distributor scope
     * @param config        synthetic data generation config (defines bundle size / seed)
     * @return summary of tuning results and any per-model errors
     */
    public TuningRunResult tuneAllModels(UUID distributorId, SyntheticDataConfig config) {
        long startMs = System.currentTimeMillis();
        log.info("[Tuning] Starting hyperparameter tuning — distributor={}", distributorId);

        SyntheticDataBundle bundle = orchestrator.generateBundle(config);
        log.info("[Tuning] Bundle generated — {} merchants", bundle.getMerchants().size());

        List<TuningResult> results = new ArrayList<>();
        List<String>       errors  = new ArrayList<>();

        List<CandidateConfig<Label>>     classifCandidates = HyperparameterGrid.classificationCandidates();
        List<CandidateConfig<Regressor>> regCandidates     = HyperparameterGrid.regressionCandidates();
        List<CandidateConfig<Event>>     anomalyCandidates = HyperparameterGrid.anomalyCandidates();

        // ── classifiers ───────────────────────────────────────────────────
        tuneClassifier(DataPhaseTracker.MODEL_CREDIT_CLASSIFIER,
                b -> featureStore.buildCreditClassifierExamples(b),
                bundle, classifCandidates, distributorId, results, errors);

        tuneClassifier(DataPhaseTracker.MODEL_STOCKOUT_PREDICTOR,
                b -> featureStore.buildStockoutPredictorExamples(b),
                bundle, classifCandidates, distributorId, results, errors);

        tuneClassifier(DataPhaseTracker.MODEL_REP_PERFORMANCE_PREDICTOR,
                b -> featureStore.buildRepPerformancePredictorExamples(b),
                bundle, classifCandidates, distributorId, results, errors);

        tuneClassifier(DataPhaseTracker.MODEL_PAYMENT_DISTRESS_CLASSIFIER,
                b -> featureStore.buildPaymentDistressExamples(b),
                bundle, classifCandidates, distributorId, results, errors);

        tuneClassifier(DataPhaseTracker.MODEL_DATA_QUALITY_DETECTOR,
                b -> featureStore.buildDataQualityExamples(b),
                bundle, classifCandidates, distributorId, results, errors);

        // ── regressors ────────────────────────────────────────────────────
        tuneRegressor(DataPhaseTracker.MODEL_CREDIT_LIMIT_REGRESSOR,
                b -> featureStore.buildCreditLimitRegressorExamples(b),
                bundle, regCandidates, distributorId, results, errors);

        tuneRegressor(DataPhaseTracker.MODEL_DEMAND_FORECASTER,
                b -> featureStore.buildDemandForecasterExamples(b),
                bundle, regCandidates, distributorId, results, errors);

        // ── anomaly detectors ─────────────────────────────────────────────
        tuneAnomalyDetector(DataPhaseTracker.MODEL_SHRINKAGE_DETECTOR,
                b -> featureStore.buildShrinkageDetectorExamples(b),
                bundle, anomalyCandidates, distributorId, results, errors);

        tuneAnomalyDetector(DataPhaseTracker.MODEL_PAYMENT_ANOMALY_DETECTOR,
                b -> featureStore.buildPaymentAnomalyExamples(b),
                bundle, anomalyCandidates, distributorId, results, errors);

        long durationMs = System.currentTimeMillis() - startMs;
        log.info("[Tuning] Complete — tuned={}, errors={}, duration={}ms",
                results.size(), errors.size(), durationMs);

        return new TuningRunResult(results, errors, errors.isEmpty(), durationMs);
    }

    // ── Private helpers ───────────────────────────────────────────────────

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
            MutableDataset<Label> dataset = toClassificationDataset(balanced, modelName);
            CrossValidationTuner.BestConfig<Label> best = cvTuner.tuneClassifier(
                    modelName, candidates, dataset);

            Model<Label> finalModel = best.config().trainer().train(dataset);
            UUID modelId = registerAndPromote(modelName, "xgboost_classification",
                    finalModel, best.config().hyperparameters(),
                    mixed.size(), 0, distributorId, best.metricValue(), best.metricName());

            results.add(new TuningResult(modelName, modelId,
                    best.config().hyperparameters(),
                    best.metricValue(), best.metricName(),
                    best.candidatesEvaluated(), best.numFolds()));

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

            MutableDataset<Regressor> dataset = toRegressionDataset(mixed, modelName);
            CrossValidationTuner.BestConfig<Regressor> best = cvTuner.tuneRegressor(
                    modelName, candidates, dataset);

            Model<Regressor> finalModel = best.config().trainer().train(dataset);
            UUID modelId = registerAndPromote(modelName, "xgboost_regression",
                    finalModel, best.config().hyperparameters(),
                    mixed.size(), 0, distributorId, best.metricValue(), best.metricName());

            results.add(new TuningResult(modelName, modelId,
                    best.config().hyperparameters(),
                    best.metricValue(), best.metricName(),
                    best.candidatesEvaluated(), best.numFolds()));

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

            // LibSVMAnomalyTrainer (one-class SVM) only accepts EXPECTED events at training
            // time. CV tuner receives ALL examples so test folds contain ANOMALOUS examples
            // for meaningful F1 evaluation. The final model is still trained on EXPECTED only.
            List<Example<Event>> trainingExamples = mixed.stream()
                    .filter(ex -> ex.getOutput().getType() == Event.EventType.EXPECTED)
                    .collect(Collectors.toList());

            if (trainingExamples.size() < MIN_ANOMALY_EXAMPLES) {
                log.warn("[Tuning] {} — too few EXPECTED examples ({} total, {} expected), skipping",
                        modelName, mixed.size(), trainingExamples.size());
                return;
            }

            // Pass ALL examples to CV so test folds include ANOMALOUS events
            CrossValidationTuner.BestConfig<Event> best = cvTuner.tuneAnomalyDetector(
                    modelName, candidates, mixed);

            // Final model trained on EXPECTED-only (LibSVM requirement)
            MutableDataset<Event> finalTrainDataset = toAnomalyDataset(trainingExamples, modelName + "_tuning");
            Model<Event> finalModel = best.config().trainer().train(finalTrainDataset);
            UUID modelId = registerAndPromote(modelName, "libsvm_anomaly",
                    finalModel, best.config().hyperparameters(),
                    mixed.size(), 0, distributorId, best.metricValue(), best.metricName());

            results.add(new TuningResult(modelName, modelId,
                    best.config().hyperparameters(),
                    best.metricValue(), best.metricName(),
                    best.candidatesEvaluated(), best.numFolds()));

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
                                     double metricValue, String metricName) {

        Map<String, Object> hparams = new LinkedHashMap<>(rawHyperparameters);
        hparams.put("tuning_metric",       metricName);
        hparams.put("tuning_metric_value", metricValue);
        hparams.put("training_phase",      "SYNTHETIC");
        hparams.put("algorithm",           algorithm);

        AIModelRegistry entry = modelRegistry.registerModel(
                modelName, algorithm, hparams, "hyperparameter_tuner");

        int featureCount = safeFeatureCount(model);
        Map<String, Object> metrics = Map.of(
                "training_phase",     "SYNTHETIC",
                "synthetic_records",  syntheticCount,
                "real_records",       realCount,
                "feature_count",      featureCount,
                metricName,           metricValue
        );
        modelRegistry.updateModelAfterTraining(
                entry.getId(), metrics, serialize(model), Map.of("feature_count", featureCount));
        modelRegistry.setDataPhaseMetadata(
                entry.getId(), DataPhase.SYNTHETIC, syntheticCount, realCount);
        modelRegistry.promoteToActive(entry.getId());
        modelRegistry.updateHyperparameters(entry.getId(), hparams);

        log.info("[Tuning] Registered and promoted {} ({}={}, id={})",
                modelName, metricName, metricValue, entry.getId());
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
