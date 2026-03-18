package com.zuqi.ai.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.tribuo.Trainer;
import org.tribuo.anomaly.Event;
import org.tribuo.anomaly.libsvm.LibSVMAnomalyTrainer;
import org.tribuo.anomaly.libsvm.SVMAnomalyType;
import org.tribuo.classification.Label;
import org.tribuo.classification.xgboost.XGBoostClassificationTrainer;
import org.tribuo.clustering.ClusterID;
import org.tribuo.clustering.kmeans.KMeansTrainer;
import org.tribuo.math.distance.L2Distance;
import org.tribuo.common.libsvm.KernelType;
import org.tribuo.common.libsvm.SVMParameters;
import org.tribuo.regression.Regressor;
import org.tribuo.regression.xgboost.XGBoostRegressionTrainer;

/**
 * Tribuo ML trainer configuration.
 *
 * <p>Defines all Tribuo trainer beans used across Phase 1.5 (synthetic bootstrap),
 * Phase 3 (hybrid training) and beyond (real data). The same trainers are reused
 * at every data phase — only the data fed to them changes via {@code DataMixer}.
 *
 * <p>Hyperparameters are driven by {@code application.yml} under the
 * {@code zuqi.ai.xgboost.*} and {@code zuqi.ai.libsvm.*} namespaces with
 * sensible defaults so the application starts without extra configuration.
 *
 * Blueprint reference: implementation_plan.md Phase 3 Task 3.1
 */
@Configuration
@Slf4j
public class TribuoConfig {

    // ── XGBoost hyperparameters ───────────────────────────────────────────

    @Value("${zuqi.ai.xgboost.classification.num-rounds:50}")
    private int classificationNumRounds;

    @Value("${zuqi.ai.xgboost.regression.num-rounds:150}")
    private int regressionNumRounds;

    // ── K-Means hyperparameters ───────────────────────────────────────────

    @Value("${zuqi.ai.kmeans.clusters:5}")
    private int kMeansClusters;

    @Value("${zuqi.ai.kmeans.iterations:100}")
    private int kMeansIterations;

    // ── LibSVM hyperparameters ────────────────────────────────────────────

    @Value("${zuqi.ai.libsvm.anomaly.nu:0.1}")
    private double anomalyNu;

    @Value("${zuqi.ai.libsvm.anomaly.gamma:0.5}")
    private double anomalyGamma;

    // ── Clustering ───────────────────────────────────────────────────────

    /**
     * K-Means clustering trainer used for merchant segmentation (Phase 2 CRM AI).
     *
     * <p>EUCLIDEAN distance, single-threaded, fixed seed 42 for reproducibility.
     */
    @Bean("kMeansTrainer")
    public Trainer<ClusterID> kMeansTrainer() {
        log.info("Configuring KMeansTrainer: clusters={}, iterations={}", kMeansClusters, kMeansIterations);
        return new KMeansTrainer(kMeansClusters, kMeansIterations, new L2Distance(), 1, 42L);
    }

    // ── Classification ────────────────────────────────────────────────────

    /**
     * XGBoost classification trainer used by:
     * credit_classifier, stockout_predictor, rep_performance_predictor,
     * payment_distress_classifier, data_quality_detector.
     */
    @Bean("xgBoostClassificationTrainer")
    public Trainer<Label> xgBoostClassificationTrainer() {
        log.info("Configuring XGBoostClassificationTrainer: numRounds={}", classificationNumRounds);
        return new XGBoostClassificationTrainer(classificationNumRounds);
    }

    // ── Regression ────────────────────────────────────────────────────────

    /**
     * XGBoost regression trainer used by:
     * demand_forecaster, credit_limit_regressor.
     */
    @Bean("xgBoostRegressionTrainer")
    public Trainer<Regressor> xgBoostRegressionTrainer() {
        log.info("Configuring XGBoostRegressionTrainer: numRounds={}", regressionNumRounds);
        return new XGBoostRegressionTrainer(regressionNumRounds);
    }

    // ── Anomaly ───────────────────────────────────────────────────────────

    /**
     * One-class LibSVM anomaly trainer used by:
     * shrinkage_detector, payment_anomaly_detector.
     *
     * <p>RBF kernel with configurable nu (expected anomaly fraction) and gamma.
     */
    @Bean("xgBoostAnomalyTrainer")
    public Trainer<Event> xgBoostAnomalyTrainer() {
        log.info("Configuring LibSVMAnomalyTrainer: nu={}, gamma={}", anomalyNu, anomalyGamma);
        SVMParameters<Event> params = new SVMParameters<>(
                new SVMAnomalyType(SVMAnomalyType.SVMMode.ONE_CLASS), KernelType.RBF);
        params.setNu(anomalyNu);
        params.setGamma(anomalyGamma);
        return new LibSVMAnomalyTrainer(params);
    }
}
