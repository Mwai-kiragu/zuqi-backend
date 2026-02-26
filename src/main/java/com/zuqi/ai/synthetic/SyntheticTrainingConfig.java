package com.zuqi.ai.synthetic;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.tribuo.Trainer;
import org.tribuo.anomaly.Event;
import org.tribuo.anomaly.libsvm.LibSVMAnomalyTrainer;
import org.tribuo.anomaly.libsvm.SVMAnomalyType;
import org.tribuo.classification.Label;
import org.tribuo.classification.xgboost.XGBoostClassificationTrainer;
import org.tribuo.common.libsvm.KernelType;
import org.tribuo.common.libsvm.SVMParameters;
import org.tribuo.regression.Regressor;
import org.tribuo.regression.xgboost.XGBoostRegressionTrainer;

/**
 * Provides injectable Tribuo trainer beans for {@link SyntheticModelTrainer}.
 *
 * <p>Separating trainer construction into a {@code @Configuration} keeps
 * {@code SyntheticModelTrainer} free of Tribuo construction details and
 * makes the trainers easily replaceable in tests via Mockito.
 */
@Configuration
public class SyntheticTrainingConfig {

    /** Rounds reduced vs. production: SYNTHETIC-phase models are bootstraps, not final. */
    private static final int SYNTHETIC_XGB_ROUNDS = 50;

    @Bean("syntheticClassificationTrainer")
    public Trainer<Label> syntheticClassificationTrainer() {
        return new XGBoostClassificationTrainer(SYNTHETIC_XGB_ROUNDS);
    }

    @Bean("syntheticRegressionTrainer")
    public Trainer<Regressor> syntheticRegressionTrainer() {
        return new XGBoostRegressionTrainer(SYNTHETIC_XGB_ROUNDS);
    }

    /**
     * One-class LibSVM anomaly trainer shared by both shrinkage_detector
     * and payment_anomaly_detector.
     *
     * <p>Parameters: RBF kernel, nu=0.1 (expect ~10% anomalies), gamma=0.5.
     */
    @Bean("syntheticAnomalyTrainer")
    public Trainer<Event> syntheticAnomalyTrainer() {
        SVMParameters<Event> params = new SVMParameters<>(
                new SVMAnomalyType(SVMAnomalyType.SVMMode.ONE_CLASS), KernelType.RBF);
        params.setNu(0.1);
        params.setGamma(0.5);
        return new LibSVMAnomalyTrainer(params);
    }
}
