package com.zuqi.domain.ai;

/**
 * Valid metric names for AI model performance tracking.
 *
 * Must match the CHECK constraint in V27__create_ai_model_performance.sql:
 *   accuracy, precision, recall, f1, auc_roc, mae, rmse, mape, r_squared,
 *   precision_at_k, recall_at_k
 *
 * Classification metrics: ACCURACY, PRECISION, RECALL, F1, AUC_ROC
 * Regression metrics:     MAE, RMSE, MAPE, R_SQUARED
 * Ranking metrics:        PRECISION_AT_K, RECALL_AT_K
 */
public enum MetricName {

    // Classification
    ACCURACY("accuracy"),
    PRECISION("precision"),
    RECALL("recall"),
    F1("f1"),
    AUC_ROC("auc_roc"),

    // Regression
    MAE("mae"),
    RMSE("rmse"),
    MAPE("mape"),
    R_SQUARED("r_squared"),

    // Ranking
    PRECISION_AT_K("precision_at_k"),
    RECALL_AT_K("recall_at_k");

    private final String dbValue;

    MetricName(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }
}
