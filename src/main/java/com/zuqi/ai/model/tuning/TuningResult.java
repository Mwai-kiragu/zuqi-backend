package com.zuqi.ai.model.tuning;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

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
 * @param holdoutMetricName   name of the holdout metric, or null when validation was skipped
 * @param holdoutMetricValue  actual holdout metric value, or NaN when validation was skipped
 * @param holdoutPassed       whether the holdout gate was cleared (always true when skipped)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TuningResult(
        @JsonProperty("modelName")           String              modelName,
        @JsonProperty("modelId")             UUID                modelId,
        @JsonProperty("bestHyperparameters") Map<String, Object> bestHyperparameters,
        @JsonProperty("bestMetricValue")     double              bestMetricValue,
        @JsonProperty("metricName")          String              metricName,
        @JsonProperty("candidatesEvaluated") int                 candidatesEvaluated,
        @JsonProperty("numFolds")            int                 numFolds,
        @JsonProperty("holdoutMetricName")   String              holdoutMetricName,
        @JsonProperty("holdoutMetricValue")  double              holdoutMetricValue,
        @JsonProperty("holdoutPassed")       boolean             holdoutPassed) {

    /**
     * Backward-compatible constructor for callers and tests that pre-date holdout validation.
     * Defaults: holdout not evaluated (skipped = passed).
     */
    public TuningResult(String modelName, UUID modelId, Map<String, Object> bestHyperparameters,
                        double bestMetricValue, String metricName,
                        int candidatesEvaluated, int numFolds) {
        this(modelName, modelId, bestHyperparameters, bestMetricValue, metricName,
                candidatesEvaluated, numFolds, null, Double.NaN, true);
    }
}
