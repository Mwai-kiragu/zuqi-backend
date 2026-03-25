package com.zuqi.ai.pipeline;

import com.zuqi.ai.cashflow.CashFlowTrainingPipeline;
import com.zuqi.ai.crm.ChurnTrainingPipeline;
import com.zuqi.ai.crm.ClvTrainingPipeline;
import com.zuqi.ai.crm.SegmentationTrainingPipeline;
import com.zuqi.ai.crm.VisitTrainingPipeline;
import com.zuqi.ai.demand.ExpiryRiskTrainingPipeline;
import com.zuqi.ai.model.ModelRegistry;
import com.zuqi.ai.prediction.RepPerformanceTrainingPipeline;
import com.zuqi.ai.pricing.PricingTrainingPipeline;
import com.zuqi.ai.recon.ReconTrainingPipeline;
import com.zuqi.ai.synthetic.DataMixer;
import com.zuqi.ai.synthetic.DataPhaseTracker;
import com.zuqi.ai.synthetic.SyntheticDataBundle;
import com.zuqi.ai.synthetic.SyntheticFeatureStore;
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
import java.util.stream.Collectors;

/**
 * Trains all ML models from a {@link SyntheticDataBundle} and registers them in the
 * {@link ModelRegistry}.
 *
 * <p>This service is <em>not</em> synthetic-specific. It trains models on whatever
 * dataset the {@link DataMixer} provides — which may be purely synthetic (Phase 1.5),
 * a hybrid of synthetic + real (Phase 3), or entirely real (Phase 6+).
 * The same trainer beans defined in {@code TribuoConfig} are reused across all phases.
 *
 * <h3>Models trained</h3>
 * <ul>
 *   <li>{@code credit_classifier}            — XGBoost classification</li>
 *   <li>{@code credit_limit_regressor}       — XGBoost regression</li>
 *   <li>{@code demand_forecaster}            — XGBoost regression</li>
 *   <li>{@code stockout_predictor}           — XGBoost classification</li>
 *   <li>{@code shrinkage_detector}           — LibSVM one-class anomaly</li>
 *   <li>{@code payment_anomaly_detector}     — LibSVM one-class anomaly</li>
 *   <li>{@code payment_distress_classifier}  — XGBoost classification</li>
 *   <li>{@code rep_performance_predictor}    — XGBoost classification</li>
 *   <li>{@code data_quality_detector}        — XGBoost classification</li>
 *   <li>{@code bank_recon_matcher}          — XGBoost classification (Model #11)</li>
 *   <li>{@code cash_flow_predictor}         — XGBoost regression (Model #12)</li>
 * </ul>
 *
 * <h3>Pipeline per model</h3>
 * <ol>
 *   <li>{@link SyntheticFeatureStore} builds labelled Tribuo {@code Example} lists.</li>
 *   <li>{@link DataMixer} blends real and synthetic examples per configured ratio.</li>
 *   <li>Injected {@link Trainer} fits the model (swappable for tests).</li>
 *   <li>{@link ModelRegistry} registers, stamps data-phase metadata, and promotes.</li>
 *   <li>{@link DataPhaseTracker} records counts and evaluates phase transition.</li>
 * </ol>
 */
@Service
@Slf4j
public class ModelTrainingService {

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
    private final ReconTrainingPipeline              reconTrainingPipeline;
    private final CashFlowTrainingPipeline           cashFlowTrainingPipeline;
    private final ExpiryRiskTrainingPipeline         expiryRiskTrainingPipeline;
    private final SegmentationTrainingPipeline       segmentationTrainingPipeline;
    private final ClvTrainingPipeline                clvTrainingPipeline;
    private final ChurnTrainingPipeline              churnTrainingPipeline;
    private final VisitTrainingPipeline              visitTrainingPipeline;
    private final PricingTrainingPipeline            pricingTrainingPipeline;
    private final RepPerformanceTrainingPipeline     repPerformanceTrainingPipeline;

    @Autowired
    public ModelTrainingService(
            SyntheticFeatureStore featureStore,
            DataMixer dataMixer,
            ModelRegistry modelRegistry,
            DataPhaseTracker phaseTracker,
            @Qualifier("xgBoostClassificationTrainer") Trainer<Label> classificationTrainer,
            @Qualifier("xgBoostRegressionTrainer")    Trainer<Regressor> regressionTrainer,
            @Qualifier("xgBoostAnomalyTrainer")       Trainer<Event> anomalyTrainer,
            ReconTrainingPipeline reconTrainingPipeline,
            CashFlowTrainingPipeline cashFlowTrainingPipeline,
            ExpiryRiskTrainingPipeline expiryRiskTrainingPipeline,
            SegmentationTrainingPipeline segmentationTrainingPipeline,
            ClvTrainingPipeline clvTrainingPipeline,
            ChurnTrainingPipeline churnTrainingPipeline,
            VisitTrainingPipeline visitTrainingPipeline,
            PricingTrainingPipeline pricingTrainingPipeline,
            RepPerformanceTrainingPipeline repPerformanceTrainingPipeline) {
        this.featureStore                   = featureStore;
        this.dataMixer                      = dataMixer;
        this.modelRegistry                  = modelRegistry;
        this.phaseTracker                   = phaseTracker;
        this.classificationTrainer          = classificationTrainer;
        this.regressionTrainer              = regressionTrainer;
        this.anomalyTrainer                 = anomalyTrainer;
        this.reconTrainingPipeline          = reconTrainingPipeline;
        this.cashFlowTrainingPipeline       = cashFlowTrainingPipeline;
        this.expiryRiskTrainingPipeline     = expiryRiskTrainingPipeline;
        this.segmentationTrainingPipeline   = segmentationTrainingPipeline;
        this.clvTrainingPipeline            = clvTrainingPipeline;
        this.churnTrainingPipeline          = churnTrainingPipeline;
        this.visitTrainingPipeline          = visitTrainingPipeline;
        this.pricingTrainingPipeline        = pricingTrainingPipeline;
        this.repPerformanceTrainingPipeline = repPerformanceTrainingPipeline;
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
    public TrainingResult trainAllModels(SyntheticDataBundle bundle, UUID distributorId) {
        long startMs = System.currentTimeMillis();
        log.info("[ModelTrainingService] Starting training — distributor={}", distributorId);

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
                log.warn("[ModelTrainingService] {} — too few examples ({}), skipping",
                        DataPhaseTracker.MODEL_CREDIT_CLASSIFIER, mixed.size());
            } else {
                long defaultCount = mixed.stream()
                        .filter(ex -> ex.getOutput().getLabel().equals("DEFAULT")).count();
                if (defaultCount == 0 || defaultCount == mixed.size()) {
                    log.warn("[ModelTrainingService] {} — single-class dataset, skipping",
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
            log.error("[ModelTrainingService] {} failed: {}",
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
                log.warn("[ModelTrainingService] {} — too few examples ({}), skipping",
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
            log.error("[ModelTrainingService] {} failed: {}",
                    DataPhaseTracker.MODEL_CREDIT_LIMIT_REGRESSOR, e.getMessage(), e);
            errors.add(DataPhaseTracker.MODEL_CREDIT_LIMIT_REGRESSOR + ": " + e.getMessage());
        }

        // ── shrinkage_detector ────────────────────────────────────────────
        try {
            List<Example<Event>> synthetic = featureStore.buildShrinkageDetectorExamples(bundle);
            List<Example<Event>> mixed = dataMixer.buildTrainingDataset(
                    DataPhaseTracker.MODEL_SHRINKAGE_DETECTOR, distributorId,
                    List.of(), synthetic);

            // LibSVMAnomalyTrainer (one-class SVM) only accepts EXPECTED events at training time
            List<Example<Event>> trainingMixed = mixed.stream()
                    .filter(ex -> ex.getOutput().getType() == Event.EventType.EXPECTED)
                    .collect(Collectors.toList());

            if (trainingMixed.size() < MIN_ANOMALY_EXAMPLES) {
                log.warn("[ModelTrainingService] {} — too few EXPECTED examples ({} total, {} expected), skipping",
                        DataPhaseTracker.MODEL_SHRINKAGE_DETECTOR, mixed.size(), trainingMixed.size());
            } else {
                MutableDataset<Event> ds = toAnomalyDataset(trainingMixed,
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
            log.error("[ModelTrainingService] {} failed: {}",
                    DataPhaseTracker.MODEL_SHRINKAGE_DETECTOR, e.getMessage(), e);
            errors.add(DataPhaseTracker.MODEL_SHRINKAGE_DETECTOR + ": " + e.getMessage());
        }

        // ── payment_anomaly_detector ──────────────────────────────────────
        try {
            List<Example<Event>> synthetic = featureStore.buildPaymentAnomalyExamples(bundle);
            List<Example<Event>> mixed = dataMixer.buildTrainingDataset(
                    DataPhaseTracker.MODEL_PAYMENT_ANOMALY_DETECTOR, distributorId,
                    List.of(), synthetic);

            // LibSVMAnomalyTrainer (one-class SVM) only accepts EXPECTED events at training time
            List<Example<Event>> trainingMixed = mixed.stream()
                    .filter(ex -> ex.getOutput().getType() == Event.EventType.EXPECTED)
                    .collect(Collectors.toList());

            if (trainingMixed.size() < MIN_ANOMALY_EXAMPLES) {
                log.warn("[ModelTrainingService] {} — too few EXPECTED examples ({} total, {} expected), skipping",
                        DataPhaseTracker.MODEL_PAYMENT_ANOMALY_DETECTOR, mixed.size(), trainingMixed.size());
            } else {
                MutableDataset<Event> ds = toAnomalyDataset(trainingMixed,
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
            log.error("[ModelTrainingService] {} failed: {}",
                    DataPhaseTracker.MODEL_PAYMENT_ANOMALY_DETECTOR, e.getMessage(), e);
            errors.add(DataPhaseTracker.MODEL_PAYMENT_ANOMALY_DETECTOR + ": " + e.getMessage());
        }

        // ── demand_forecaster ─────────────────────────────────────────────
        try {
            List<Example<Regressor>> synthetic = featureStore.buildDemandForecasterExamples(bundle);
            List<Example<Regressor>> mixed = dataMixer.buildTrainingDataset(
                    DataPhaseTracker.MODEL_DEMAND_FORECASTER, distributorId,
                    List.of(), synthetic);

            if (mixed.size() < MIN_REGRESSION_EXAMPLES) {
                log.warn("[ModelTrainingService] {} — too few examples ({}), skipping",
                        DataPhaseTracker.MODEL_DEMAND_FORECASTER, mixed.size());
            } else {
                MutableDataset<Regressor> ds = toRegressionDataset(mixed);
                Model<Regressor> model = regressionTrainer.train(ds);
                UUID id = registerAndPromote(DataPhaseTracker.MODEL_DEMAND_FORECASTER,
                        "xgboost_regression", model, mixed.size(), 0, distributorId);
                modelIds.put(DataPhaseTracker.MODEL_DEMAND_FORECASTER, id);
                counts.put(DataPhaseTracker.MODEL_DEMAND_FORECASTER, mixed.size());
                phaseTracker.updateCounts(DataPhaseTracker.MODEL_DEMAND_FORECASTER,
                        distributorId, 0, mixed.size());
                phaseTracker.evaluatePhase(DataPhaseTracker.MODEL_DEMAND_FORECASTER, distributorId);
            }
        } catch (Exception e) {
            log.error("[ModelTrainingService] {} failed: {}",
                    DataPhaseTracker.MODEL_DEMAND_FORECASTER, e.getMessage(), e);
            errors.add(DataPhaseTracker.MODEL_DEMAND_FORECASTER + ": " + e.getMessage());
        }

        // ── stockout_predictor ────────────────────────────────────────────
        try {
            List<Example<Label>> synthetic = featureStore.buildStockoutPredictorExamples(bundle);
            List<Example<Label>> mixed = dataMixer.buildTrainingDataset(
                    DataPhaseTracker.MODEL_STOCKOUT_PREDICTOR, distributorId,
                    List.of(), synthetic);

            if (mixed.size() < MIN_CLASSIFICATION_EXAMPLES) {
                log.warn("[ModelTrainingService] {} — too few examples ({}), skipping",
                        DataPhaseTracker.MODEL_STOCKOUT_PREDICTOR, mixed.size());
            } else if (isSingleClass(mixed)) {
                log.warn("[ModelTrainingService] {} — single-class dataset, skipping",
                        DataPhaseTracker.MODEL_STOCKOUT_PREDICTOR);
            } else {
                MutableDataset<Label> ds = toClassificationDataset(mixed);
                Model<Label> model = classificationTrainer.train(ds);
                UUID id = registerAndPromote(DataPhaseTracker.MODEL_STOCKOUT_PREDICTOR,
                        "xgboost_classification", model, mixed.size(), 0, distributorId);
                modelIds.put(DataPhaseTracker.MODEL_STOCKOUT_PREDICTOR, id);
                counts.put(DataPhaseTracker.MODEL_STOCKOUT_PREDICTOR, mixed.size());
                phaseTracker.updateCounts(DataPhaseTracker.MODEL_STOCKOUT_PREDICTOR,
                        distributorId, 0, mixed.size());
                phaseTracker.evaluatePhase(DataPhaseTracker.MODEL_STOCKOUT_PREDICTOR, distributorId);
            }
        } catch (Exception e) {
            log.error("[ModelTrainingService] {} failed: {}",
                    DataPhaseTracker.MODEL_STOCKOUT_PREDICTOR, e.getMessage(), e);
            errors.add(DataPhaseTracker.MODEL_STOCKOUT_PREDICTOR + ": " + e.getMessage());
        }

        // ── rep_performance_predictor ─────────────────────────────────────
        // Delegates to RepPerformanceTrainingPipeline which generates 400 synthetic
        // snapshots and trains an XGBoost regressor (score 0–100) with quality gate R²≥0.70.
        try {
            RepPerformanceTrainingPipeline.TrainingPipelineResult repResult =
                    repPerformanceTrainingPipeline.runPipeline();
            if (repResult.success() && repResult.passedQualityGate() && repResult.modelId() != null) {
                modelIds.put(DataPhaseTracker.MODEL_REP_PERFORMANCE_PREDICTOR, repResult.modelId());
                counts.put(DataPhaseTracker.MODEL_REP_PERFORMANCE_PREDICTOR, repResult.trainSize());
                phaseTracker.updateCounts(DataPhaseTracker.MODEL_REP_PERFORMANCE_PREDICTOR,
                        distributorId, 0, repResult.trainSize());
                phaseTracker.evaluatePhase(DataPhaseTracker.MODEL_REP_PERFORMANCE_PREDICTOR, distributorId);
            } else {
                String reason = repResult.errorMessage() != null ? repResult.errorMessage()
                        : "Quality gate not passed (R²=" + String.format("%.3f", repResult.r2()) + ")";
                log.warn("[ModelTrainingService] {} — {}", DataPhaseTracker.MODEL_REP_PERFORMANCE_PREDICTOR, reason);
                errors.add(DataPhaseTracker.MODEL_REP_PERFORMANCE_PREDICTOR + ": " + reason);
            }
        } catch (Exception e) {
            log.error("[ModelTrainingService] {} failed: {}",
                    DataPhaseTracker.MODEL_REP_PERFORMANCE_PREDICTOR, e.getMessage(), e);
            errors.add(DataPhaseTracker.MODEL_REP_PERFORMANCE_PREDICTOR + ": " + e.getMessage());
        }

        // ── payment_distress_classifier ───────────────────────────────────
        try {
            List<Example<Label>> synthetic = featureStore.buildPaymentDistressExamples(bundle);
            List<Example<Label>> mixed = dataMixer.buildTrainingDataset(
                    DataPhaseTracker.MODEL_PAYMENT_DISTRESS_CLASSIFIER, distributorId,
                    List.of(), synthetic);

            if (mixed.size() < MIN_CLASSIFICATION_EXAMPLES) {
                log.warn("[ModelTrainingService] {} — too few examples ({}), skipping",
                        DataPhaseTracker.MODEL_PAYMENT_DISTRESS_CLASSIFIER, mixed.size());
            } else if (isSingleClass(mixed)) {
                log.warn("[ModelTrainingService] {} — single-class dataset, skipping",
                        DataPhaseTracker.MODEL_PAYMENT_DISTRESS_CLASSIFIER);
            } else {
                MutableDataset<Label> ds = toClassificationDataset(mixed);
                Model<Label> model = classificationTrainer.train(ds);
                UUID id = registerAndPromote(DataPhaseTracker.MODEL_PAYMENT_DISTRESS_CLASSIFIER,
                        "xgboost_classification", model, mixed.size(), 0, distributorId);
                modelIds.put(DataPhaseTracker.MODEL_PAYMENT_DISTRESS_CLASSIFIER, id);
                counts.put(DataPhaseTracker.MODEL_PAYMENT_DISTRESS_CLASSIFIER, mixed.size());
                phaseTracker.updateCounts(DataPhaseTracker.MODEL_PAYMENT_DISTRESS_CLASSIFIER,
                        distributorId, 0, mixed.size());
                phaseTracker.evaluatePhase(DataPhaseTracker.MODEL_PAYMENT_DISTRESS_CLASSIFIER, distributorId);
            }
        } catch (Exception e) {
            log.error("[ModelTrainingService] {} failed: {}",
                    DataPhaseTracker.MODEL_PAYMENT_DISTRESS_CLASSIFIER, e.getMessage(), e);
            errors.add(DataPhaseTracker.MODEL_PAYMENT_DISTRESS_CLASSIFIER + ": " + e.getMessage());
        }

        // ── data_quality_detector ─────────────────────────────────────────
        try {
            List<Example<Label>> synthetic = featureStore.buildDataQualityExamples(bundle);
            List<Example<Label>> mixed = dataMixer.buildTrainingDataset(
                    DataPhaseTracker.MODEL_DATA_QUALITY_DETECTOR, distributorId,
                    List.of(), synthetic);

            if (mixed.size() < MIN_CLASSIFICATION_EXAMPLES) {
                log.warn("[ModelTrainingService] {} — too few examples ({}), skipping",
                        DataPhaseTracker.MODEL_DATA_QUALITY_DETECTOR, mixed.size());
            } else if (isSingleClass(mixed)) {
                log.warn("[ModelTrainingService] {} — single-class dataset, skipping",
                        DataPhaseTracker.MODEL_DATA_QUALITY_DETECTOR);
            } else {
                MutableDataset<Label> ds = toClassificationDataset(mixed);
                Model<Label> model = classificationTrainer.train(ds);
                UUID id = registerAndPromote(DataPhaseTracker.MODEL_DATA_QUALITY_DETECTOR,
                        "xgboost_classification", model, mixed.size(), 0, distributorId);
                modelIds.put(DataPhaseTracker.MODEL_DATA_QUALITY_DETECTOR, id);
                counts.put(DataPhaseTracker.MODEL_DATA_QUALITY_DETECTOR, mixed.size());
                phaseTracker.updateCounts(DataPhaseTracker.MODEL_DATA_QUALITY_DETECTOR,
                        distributorId, 0, mixed.size());
                phaseTracker.evaluatePhase(DataPhaseTracker.MODEL_DATA_QUALITY_DETECTOR, distributorId);
            }
        } catch (Exception e) {
            log.error("[ModelTrainingService] {} failed: {}",
                    DataPhaseTracker.MODEL_DATA_QUALITY_DETECTOR, e.getMessage(), e);
            errors.add(DataPhaseTracker.MODEL_DATA_QUALITY_DETECTOR + ": " + e.getMessage());
        }

        // ── expiry_risk_predictor (Model #10) ─────────────────────────────
        try {
            ExpiryRiskTrainingPipeline.TrainingResult expiryResult = expiryRiskTrainingPipeline.runPipeline();
            if (expiryResult.success()) {
                modelIds.put(ExpiryRiskTrainingPipeline.MODEL_NAME, expiryResult.modelId());
                counts.put(ExpiryRiskTrainingPipeline.MODEL_NAME, 0);
            } else {
                log.warn("[ModelTrainingService] {} — {}", ExpiryRiskTrainingPipeline.MODEL_NAME,
                        expiryResult.errorMessage());
                errors.add(ExpiryRiskTrainingPipeline.MODEL_NAME + ": " + expiryResult.errorMessage());
            }
        } catch (Exception e) {
            log.error("[ModelTrainingService] {} failed: {}", ExpiryRiskTrainingPipeline.MODEL_NAME,
                    e.getMessage(), e);
            errors.add(ExpiryRiskTrainingPipeline.MODEL_NAME + ": " + e.getMessage());
        }

        // ── customer_segmenter (K-Means) ──────────────────────────────────
        try {
            SegmentationTrainingPipeline.TrainingResult segResult = segmentationTrainingPipeline.runPipeline();
            if (segResult.success()) {
                modelIds.put(SegmentationTrainingPipeline.MODEL_NAME, segResult.modelId());
                counts.put(SegmentationTrainingPipeline.MODEL_NAME, 0);
            } else {
                log.warn("[ModelTrainingService] {} — {}", SegmentationTrainingPipeline.MODEL_NAME,
                        segResult.errorMessage());
                errors.add(SegmentationTrainingPipeline.MODEL_NAME + ": " + segResult.errorMessage());
            }
        } catch (Exception e) {
            log.error("[ModelTrainingService] {} failed: {}", SegmentationTrainingPipeline.MODEL_NAME,
                    e.getMessage(), e);
            errors.add(SegmentationTrainingPipeline.MODEL_NAME + ": " + e.getMessage());
        }

        // ── customer_clv_predictor ────────────────────────────────────────
        try {
            ClvTrainingPipeline.TrainingResult clvResult = clvTrainingPipeline.runPipeline();
            if (clvResult.success()) {
                modelIds.put(ClvTrainingPipeline.MODEL_NAME, clvResult.modelId());
                counts.put(ClvTrainingPipeline.MODEL_NAME, 0);
            } else {
                log.warn("[ModelTrainingService] {} — {}", ClvTrainingPipeline.MODEL_NAME,
                        clvResult.errorMessage());
                errors.add(ClvTrainingPipeline.MODEL_NAME + ": " + clvResult.errorMessage());
            }
        } catch (Exception e) {
            log.error("[ModelTrainingService] {} failed: {}", ClvTrainingPipeline.MODEL_NAME,
                    e.getMessage(), e);
            errors.add(ClvTrainingPipeline.MODEL_NAME + ": " + e.getMessage());
        }

        // ── churn_predictor ───────────────────────────────────────────────
        try {
            ChurnTrainingPipeline.TrainingResult churnResult = churnTrainingPipeline.runPipeline();
            if (churnResult.success()) {
                modelIds.put(ChurnTrainingPipeline.MODEL_NAME, churnResult.modelId());
                counts.put(ChurnTrainingPipeline.MODEL_NAME, 0);
            } else {
                log.warn("[ModelTrainingService] {} — {}", ChurnTrainingPipeline.MODEL_NAME,
                        churnResult.errorMessage());
                errors.add(ChurnTrainingPipeline.MODEL_NAME + ": " + churnResult.errorMessage());
            }
        } catch (Exception e) {
            log.error("[ModelTrainingService] {} failed: {}", ChurnTrainingPipeline.MODEL_NAME,
                    e.getMessage(), e);
            errors.add(ChurnTrainingPipeline.MODEL_NAME + ": " + e.getMessage());
        }

        // ── visit_optimizer ───────────────────────────────────────────────
        try {
            VisitTrainingPipeline.TrainingResult visitResult = visitTrainingPipeline.runPipeline();
            if (visitResult.success()) {
                modelIds.put(VisitTrainingPipeline.MODEL_NAME, visitResult.modelId());
                counts.put(VisitTrainingPipeline.MODEL_NAME, 0);
            } else {
                log.warn("[ModelTrainingService] {} — {}", VisitTrainingPipeline.MODEL_NAME,
                        visitResult.errorMessage());
                errors.add(VisitTrainingPipeline.MODEL_NAME + ": " + visitResult.errorMessage());
            }
        } catch (Exception e) {
            log.error("[ModelTrainingService] {} failed: {}", VisitTrainingPipeline.MODEL_NAME,
                    e.getMessage(), e);
            errors.add(VisitTrainingPipeline.MODEL_NAME + ": " + e.getMessage());
        }

        // ── smart_pricing_recommender ─────────────────────────────────────
        try {
            PricingTrainingPipeline.TrainingResult pricingResult = pricingTrainingPipeline.runPipeline();
            if (pricingResult.success()) {
                modelIds.put(PricingTrainingPipeline.MODEL_NAME, pricingResult.modelId());
                counts.put(PricingTrainingPipeline.MODEL_NAME, 0);
            } else {
                log.warn("[ModelTrainingService] {} — {}", PricingTrainingPipeline.MODEL_NAME,
                        pricingResult.errorMessage());
                errors.add(PricingTrainingPipeline.MODEL_NAME + ": " + pricingResult.errorMessage());
            }
        } catch (Exception e) {
            log.error("[ModelTrainingService] {} failed: {}", PricingTrainingPipeline.MODEL_NAME,
                    e.getMessage(), e);
            errors.add(PricingTrainingPipeline.MODEL_NAME + ": " + e.getMessage());
        }

        // ── bank_recon_matcher (Model #11) ────────────────────────────────
        try {
            ReconTrainingPipeline.TrainingResult reconResult = reconTrainingPipeline.runPipeline();
            if (reconResult.success()) {
                modelIds.put(ReconTrainingPipeline.MODEL_NAME, reconResult.modelId());
                counts.put(ReconTrainingPipeline.MODEL_NAME, 0);
            } else {
                log.warn("[ModelTrainingService] {} — {}", ReconTrainingPipeline.MODEL_NAME,
                        reconResult.errorMessage());
                errors.add(ReconTrainingPipeline.MODEL_NAME + ": " + reconResult.errorMessage());
            }
        } catch (Exception e) {
            log.error("[ModelTrainingService] {} failed: {}", ReconTrainingPipeline.MODEL_NAME,
                    e.getMessage(), e);
            errors.add(ReconTrainingPipeline.MODEL_NAME + ": " + e.getMessage());
        }

        // ── cash_flow_predictor (Model #12) ───────────────────────────────
        try {
            CashFlowTrainingPipeline.TrainingResult cfResult = cashFlowTrainingPipeline.runPipeline();
            if (cfResult.success()) {
                modelIds.put(CashFlowTrainingPipeline.MODEL_NAME, cfResult.modelId());
                counts.put(CashFlowTrainingPipeline.MODEL_NAME, 0);
            } else {
                log.warn("[ModelTrainingService] {} — {}", CashFlowTrainingPipeline.MODEL_NAME,
                        cfResult.errorMessage());
                errors.add(CashFlowTrainingPipeline.MODEL_NAME + ": " + cfResult.errorMessage());
            }
        } catch (Exception e) {
            log.error("[ModelTrainingService] {} failed: {}", CashFlowTrainingPipeline.MODEL_NAME,
                    e.getMessage(), e);
            errors.add(CashFlowTrainingPipeline.MODEL_NAME + ": " + e.getMessage());
        }

        long durationMs = System.currentTimeMillis() - startMs;
        log.info("[ModelTrainingService] Complete — trained={}, errors={}, duration={}ms",
                modelIds.size(), errors.size(), durationMs);

        return new TrainingResult(modelIds, counts, errors.isEmpty(), errors, durationMs);
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
                modelName, algorithm, hyperparams, "model_training_service");

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

        log.info("[ModelTrainingService] Registered and promoted {} (synthetic={}, id={})",
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

    /** Returns {@code true} when all examples share the same label (XGBoost would fail). */
    private boolean isSingleClass(List<Example<Label>> examples) {
        if (examples.isEmpty()) return true;
        return examples.stream()
                .map(ex -> ex.getOutput().getLabel())
                .distinct()
                .count() <= 1;
    }

    /** Convert labelled classification examples to a Tribuo MutableDataset. */
    private MutableDataset<Label> toClassificationDataset(List<Example<Label>> examples) {
        SimpleDataSourceProvenance prov = new SimpleDataSourceProvenance(
                "classification", new LabelFactory());
        MutableDataset<Label> ds = new MutableDataset<>(prov, new LabelFactory());
        examples.forEach(ds::add);
        return ds;
    }

    /** Convert labelled regression examples to a Tribuo MutableDataset. */
    private MutableDataset<Regressor> toRegressionDataset(List<Example<Regressor>> examples) {
        SimpleDataSourceProvenance prov = new SimpleDataSourceProvenance(
                "regression", new RegressionFactory());
        MutableDataset<Regressor> ds = new MutableDataset<>(prov, new RegressionFactory());
        examples.forEach(ds::add);
        return ds;
    }

    /** Convert anomaly detection examples (EXPECTED only) to a Tribuo MutableDataset. */
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
     * Immutable summary of a model training run.
     *
     * @param trainedModelIds map of modelName → registry UUID for successfully promoted models
     * @param exampleCounts   map of modelName → number of training examples used
     * @param success         true when all models trained and promoted without errors
     * @param errors          list of per-model error messages (empty when success=true)
     * @param durationMs      wall-clock time of the entire training run
     */
    public record TrainingResult(
            Map<String, UUID>    trainedModelIds,
            Map<String, Integer> exampleCounts,
            boolean              success,
            List<String>         errors,
            long                 durationMs) {}
}
