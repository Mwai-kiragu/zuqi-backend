package com.zuqi.ai.model.tuning;

import java.util.Map;
import java.util.UUID;

/**
 * Immutable result of a hyperparameter tuning run for one ML model.
 *
 * @param modelName           model name (e.g. {@code "credit_classifier"})
 * @param modelId             registry UUID of the newly promoted model
 * @param bestHyperparameters best hyperparameter map found by cross-validation
 * @param bestMetricValue     best CV metric (macro-F1 for classifiers, RMSE for regressors,
 *                            F1 for anomaly detectors)
 * @param metricName          name of the optimised metric (for display / logging)
 * @param candidatesEvaluated number of candidate configurations evaluated
 * @param numFolds            k used in k-fold cross-validation
 */
public record TuningResult(
        String              modelName,
        UUID                modelId,
        Map<String, Object> bestHyperparameters,
        double              bestMetricValue,
        String              metricName,
        int                 candidatesEvaluated,
        int                 numFolds) {}
