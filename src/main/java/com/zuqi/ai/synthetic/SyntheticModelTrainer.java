package com.zuqi.ai.synthetic;

import com.zuqi.ai.model.ModelRegistry;
import com.zuqi.domain.ai.AIModelRegistry;
import com.zuqi.domain.ai.DataPhase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.tribuo.Example;
import org.tribuo.Model;
import org.tribuo.MutableDataset;
import org.tribuo.Trainer;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Trains ML models from a {@link SyntheticDataBundle} and registers them in the
 * {@link ModelRegistry} with {@code data_phase = SYNTHETIC}.
 *
 * <h3>Models trained</h3>
 * <ul>
 *   <li>{@code credit_classifier}        — XGBoost classification</li>
 *   <li>{@code credit_limit_regressor}   — XGBoost regression</li>
 *   <li>{@code shrinkage_detector}       — LibSVM one-class anomaly</li>
 *   <li>{@code payment_anomaly_detector} — LibSVM one-class anomaly</li>
 * </ul>
 *
 * <h3>Pipeline per model</h3>
 * <ol>
 *   <li>{@link SyntheticFeatureStore} builds labelled Tribuo {@code Example} lists.</li>
 *   <li>{@link DataMixer} blends real (empty at SYNTHETIC phase) and synthetic examples.</li>
 *   <li>Injected {@link Trainer} fits the model (swappable for tests).</li>
 *   <li>{@link ModelRegistry} registers, stamps data-phase metadata, and promotes.</li>
 *   <li>{@link DataPhaseTracker} records synthetic count and evaluates phase transition.</li>
 * </ol>
 */
@Service
@Slf4j
public class SyntheticModelTrainer {

    // ── Minimum dataset sizes ────────────────────────────────────────────────

    static final int MIN_CLASSIFICATION_EXAMPLES = 10;
    static final int MIN_REGRESSION_EXAMPLES     = 10;
    static final int MIN_ANOMALY_EXAMPLES        = 5;

    // ── Dependencies ─────────────────────────────────────────────────────────

    private final SyntheticFeatureStore featureStore;
    private final DataMixer             dataMixer;
    private final ModelRegistry         modelRegistry;
    private final DataPhaseTracker      phaseTracker;
    private final Trainer<Label>        classificationTrainer;
    private final Trainer<Regressor>    regressionTrainer;
    private final Trainer<Event>        anomalyTrainer;

    @Autowired
    public SyntheticModelTrainer(
            SyntheticFeatureStore featureStore,
            DataMixer dataMixer,
            ModelRegistry modelRegistry,
            DataPhaseTracker phaseTracker,
            @Qualifier("syntheticClassificationTrainer") Trainer<Label> classificationTrainer,
            @Qualifier("syntheticRegressionTrainer")    Trainer<Regressor> regressionTrainer,
            @Qualifier("syntheticAnomalyTrainer")       Trainer<Event> anomalyTrainer) {
        this.featureStore           = featureStore;
        this.dataMixer              = dataMixer;
        this.modelRegistry          = modelRegistry;
        this.phaseTracker           = phaseTracker;
        this.classificationTrainer  = classificationTrainer;
        this.regressionTrainer      = regressionTrainer;
        this.anomalyTrainer         = anomalyTrainer;
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Train all supported models from the given bundle and register them
     * in the model registry.
     *
     * <p>Failures for individual models are caught, logged, and collected
     * in the result — they do not abort training for the remaining models.
     *
     * @param bundle        complete synthetic dataset
     * @param distributorId distributor scope (null = global)
     * @return summary of trained model IDs, example counts, and any errors
     */
    public SyntheticTrainingResult trainAllModels(SyntheticDataBundle bundle,
                                                   UUID distributorId) {
        long startMs = System.currentTimeMillis();
        log.info("[SyntheticTrainer] Starting synthetic training — distributor={}", distributorId);

        Map<String, UUID>    modelIds = new LinkedHashMap<>();
        Map<String, Integer> counts   = new LinkedHashMap<>();
        List<String>         errors   = new ArrayList<>();

        // ── credit_classifier ──────────────────────────────────────────────
        try {
            List<Example<Label>> synthetic = featureStore.buildCreditClassifierExamples(bundle);
            List<Example<Label>> mixed = dataMixer.buildTrainingDataset(
                    DataPhaseTracker.MODEL_CREDIT_CLASSIFIER, distributorId,
                    List.of(), synthetic);

            if (mixed.size() < MIN_CLASSIFICATION_EXAMPLES) {
                log.warn("[SyntheticTrainer] {} — too few examples ({}), skipping",
                        DataPhaseTracker.MODEL_CREDIT_CLASSIFIER, mixed.size());
            } else {
                long defaultCount = mixed.stream()
                        .filter(ex -> ex.getOutput().getLabel().equals("DEFAULT")).count();
                if (defaultCount == 0 || defaultCount == mixed.size()) {
                    log.warn("[SyntheticTrainer] {} — single-class dataset, skipping",
                            DataPhaseTracker.MODEL_CREDIT_CLASSIFIER);
                } else {
                    MutableDataset<Label> ds = toClassificationDataset(mixed);
                    Model<Label> model = classificationTrainer.train(ds);
                    UUID id = registerAndPromote(DataPhaseTracker.MODEL_CREDIT_CLASSIFIER,
                            "xgboost_classification", model, mixed.size(), 0, distributorId);
                    modelIds.put(DataPhaseTracker.MODEL_CREDIT_CLASSIFIER, id);
                    counts.put(DataPhaseTracker.MODEL_CREDIT_CLASSIFIER, mixed.size());
                    phaseTracker.updateCounts(DataPhaseTracker.MODEL_CREDIT_CLASSIFIER,
                            distributorId, 0, mixed.size());
                    phaseTracker.evaluatePhase(DataPhaseTracker.MODEL_CREDIT_CLASSIFIER, distributorId);
                }
            }
        } catch (Exception e) {
            log.error("[SyntheticTrainer] {} failed: {}",
                    DataPhaseTracker.MODEL_CREDIT_CLASSIFIER, e.getMessage(), e);
            errors.add(DataPhaseTracker.MODEL_CREDIT_CLASSIFIER + ": " + e.getMessage());
        }

        // ── credit_limit_regressor ─────────────────────────────────────────
        try {
            List<Example<Regressor>> synthetic = featureStore.buildCreditLimitRegressorExamples(bundle);
            List<Example<Regressor>> mixed = dataMixer.buildTrainingDataset(
                    DataPhaseTracker.MODEL_CREDIT_LIMIT_REGRESSOR, distributorId,
                    List.of(), synthetic);

            if (mixed.size() < MIN_REGRESSION_EXAMPLES) {
                log.warn("[SyntheticTrainer] {} — too few examples ({}), skipping",
                        DataPhaseTracker.MODEL_CREDIT_LIMIT_REGRESSOR, mixed.size());
            } else {
                MutableDataset<Regressor> ds = toRegressionDataset(mixed);
                Model<Regressor> model = regressionTrainer.train(ds);
                UUID id = registerAndPromote(DataPhaseTracker.MODEL_CREDIT_LIMIT_REGRESSOR,
                        "xgboost_regression", model, mixed.size(), 0, distributorId);
                modelIds.put(DataPhaseTracker.MODEL_CREDIT_LIMIT_REGRESSOR, id);
                counts.put(DataPhaseTracker.MODEL_CREDIT_LIMIT_REGRESSOR, mixed.size());
                phaseTracker.updateCounts(DataPhaseTracker.MODEL_CREDIT_LIMIT_REGRESSOR,
                        distributorId, 0, mixed.size());
                phaseTracker.evaluatePhase(DataPhaseTracker.MODEL_CREDIT_LIMIT_REGRESSOR, distributorId);
            }
        } catch (Exception e) {
            log.error("[SyntheticTrainer] {} failed: {}",
                    DataPhaseTracker.MODEL_CREDIT_LIMIT_REGRESSOR, e.getMessage(), e);
            errors.add(DataPhaseTracker.MODEL_CREDIT_LIMIT_REGRESSOR + ": " + e.getMessage());
        }

        // ── shrinkage_detector ────────────────────────────────────────────
        try {
            List<Example<Event>> synthetic = featureStore.buildShrinkageDetectorExamples(bundle);
            List<Example<Event>> mixed = dataMixer.buildTrainingDataset(
                    DataPhaseTracker.MODEL_SHRINKAGE_DETECTOR, distributorId,
                    List.of(), synthetic);

            if (mixed.size() < MIN_ANOMALY_EXAMPLES) {
                log.warn("[SyntheticTrainer] {} — too few examples ({}), skipping",
                        DataPhaseTracker.MODEL_SHRINKAGE_DETECTOR, mixed.size());
            } else {
                MutableDataset<Event> ds = toAnomalyDataset(mixed,
                        DataPhaseTracker.MODEL_SHRINKAGE_DETECTOR + "_training");
                Model<Event> model = anomalyTrainer.train(ds);
                UUID id = registerAndPromote(DataPhaseTracker.MODEL_SHRINKAGE_DETECTOR,
                        "libsvm_anomaly", model, mixed.size(), 0, distributorId);
                modelIds.put(DataPhaseTracker.MODEL_SHRINKAGE_DETECTOR, id);
                counts.put(DataPhaseTracker.MODEL_SHRINKAGE_DETECTOR, mixed.size());
                phaseTracker.updateCounts(DataPhaseTracker.MODEL_SHRINKAGE_DETECTOR,
                        distributorId, 0, mixed.size());
                phaseTracker.evaluatePhase(DataPhaseTracker.MODEL_SHRINKAGE_DETECTOR, distributorId);
            }
        } catch (Exception e) {
            log.error("[SyntheticTrainer] {} failed: {}",
                    DataPhaseTracker.MODEL_SHRINKAGE_DETECTOR, e.getMessage(), e);
            errors.add(DataPhaseTracker.MODEL_SHRINKAGE_DETECTOR + ": " + e.getMessage());
        }

        // ── payment_anomaly_detector ──────────────────────────────────────
        try {
            List<Example<Event>> synthetic = featureStore.buildPaymentAnomalyExamples(bundle);
            List<Example<Event>> mixed = dataMixer.buildTrainingDataset(
                    DataPhaseTracker.MODEL_PAYMENT_ANOMALY_DETECTOR, distributorId,
                    List.of(), synthetic);

            if (mixed.size() < MIN_ANOMALY_EXAMPLES) {
                log.warn("[SyntheticTrainer] {} — too few examples ({}), skipping",
                        DataPhaseTracker.MODEL_PAYMENT_ANOMALY_DETECTOR, mixed.size());
            } else {
                MutableDataset<Event> ds = toAnomalyDataset(mixed,
                        DataPhaseTracker.MODEL_PAYMENT_ANOMALY_DETECTOR + "_training");
                Model<Event> model = anomalyTrainer.train(ds);
                UUID id = registerAndPromote(DataPhaseTracker.MODEL_PAYMENT_ANOMALY_DETECTOR,
                        "libsvm_anomaly", model, mixed.size(), 0, distributorId);
                modelIds.put(DataPhaseTracker.MODEL_PAYMENT_ANOMALY_DETECTOR, id);
                counts.put(DataPhaseTracker.MODEL_PAYMENT_ANOMALY_DETECTOR, mixed.size());
                phaseTracker.updateCounts(DataPhaseTracker.MODEL_PAYMENT_ANOMALY_DETECTOR,
                        distributorId, 0, mixed.size());
                phaseTracker.evaluatePhase(DataPhaseTracker.MODEL_PAYMENT_ANOMALY_DETECTOR, distributorId);
            }
        } catch (Exception e) {
            log.error("[SyntheticTrainer] {} failed: {}",
                    DataPhaseTracker.MODEL_PAYMENT_ANOMALY_DETECTOR, e.getMessage(), e);
            errors.add(DataPhaseTracker.MODEL_PAYMENT_ANOMALY_DETECTOR + ": " + e.getMessage());
        }

        long durationMs = System.currentTimeMillis() - startMs;
        log.info("[SyntheticTrainer] Complete — trained={}, errors={}, duration={}ms",
                modelIds.size(), errors.size(), durationMs);

        return new SyntheticTrainingResult(modelIds, counts,
                errors.isEmpty(), errors, durationMs);
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    /**
     * Register a trained model in TRAINING status, stamp data-phase metadata,
     * update to EVALUATING, and promote to ACTIVE.
     *
     * @return the registry ID of the promoted model
     */
    private UUID registerAndPromote(String modelName, String algorithm,
                                     Model<?> model, int syntheticCount, int realCount,
                                     UUID distributorId) {
        Map<String, Object> hyperparams = Map.of(
                "algorithm",      algorithm,
                "training_phase", "SYNTHETIC"
        );

        AIModelRegistry registry = modelRegistry.registerModel(
                modelName, algorithm, hyperparams, "synthetic_model_trainer");

        byte[] binary = serializeModel(model);

        int featureCount = safeFeatureCount(model);
        Map<String, Object> metrics = Map.of(
                "training_phase",     "SYNTHETIC",
                "synthetic_records",  syntheticCount,
                "real_records",       realCount,
                "feature_count",      featureCount
        );
        Map<String, Object> featureCols = Map.of("feature_count", featureCount);

        modelRegistry.updateModelAfterTraining(registry.getId(), metrics, binary, featureCols);
        modelRegistry.setDataPhaseMetadata(registry.getId(),
                DataPhase.SYNTHETIC, syntheticCount, realCount);
        modelRegistry.promoteToActive(registry.getId());

        log.info("[SyntheticTrainer] Registered and promoted {} (synthetic={}, id={})",
                modelName, syntheticCount, registry.getId());
        return registry.getId();
    }

    private int safeFeatureCount(Model<?> model) {
        try {
            return model.getFeatureIDMap().size();
        } catch (Exception e) {
            return 0;
        }
    }

    /** Convert labelled classification examples to a Tribuo MutableDataset. */
    private MutableDataset<Label> toClassificationDataset(List<Example<Label>> examples) {
        SimpleDataSourceProvenance prov = new SimpleDataSourceProvenance(
                "synthetic_classification", new LabelFactory());
        MutableDataset<Label> ds = new MutableDataset<>(prov, new LabelFactory());
        examples.forEach(ds::add);
        return ds;
    }

    /** Convert labelled regression examples to a Tribuo MutableDataset. */
    private MutableDataset<Regressor> toRegressionDataset(List<Example<Regressor>> examples) {
        SimpleDataSourceProvenance prov = new SimpleDataSourceProvenance(
                "synthetic_regression", new RegressionFactory());
        MutableDataset<Regressor> ds = new MutableDataset<>(prov, new RegressionFactory());
        examples.forEach(ds::add);
        return ds;
    }

    /** Convert anomaly detection examples (EXPECTED + ANOMALOUS) to a Tribuo MutableDataset. */
    private MutableDataset<Event> toAnomalyDataset(List<Example<Event>> examples,
                                                    String provenance) {
        AnomalyFactory factory = new AnomalyFactory();
        SimpleDataSourceProvenance prov = new SimpleDataSourceProvenance(provenance, factory);
        MutableDataset<Event> ds = new MutableDataset<>(prov, factory);
        examples.forEach(ds::add);
        return ds;
    }

    /** Serialize a Tribuo model to a byte array for storage in the registry. */
    private byte[] serializeModel(Model<?> model) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(model);
            oos.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Model serialization failed for " + model.getName(), e);
        }
    }

    // ── Result record ────────────────────────────────────────────────────────

    /**
     * Immutable summary of a synthetic training run.
     *
     * @param trainedModelIds map of modelName → registry UUID for successfully promoted models
     * @param exampleCounts   map of modelName → number of training examples used
     * @param success         true when all 4 models trained and promoted without errors
     * @param errors          list of per-model error messages (empty when success=true)
     * @param durationMs      wall-clock time of the entire training run
     */
    public record SyntheticTrainingResult(
            Map<String, UUID>    trainedModelIds,
            Map<String, Integer> exampleCounts,
            boolean              success,
            List<String>         errors,
            long                 durationMs) {}
}
