-- AI Model Performance Tracking
-- Tracks model accuracy metrics over time for drift detection and quality monitoring
-- Part of Phase 1: Foundation Infrastructure

CREATE TABLE IF NOT EXISTS ai_model_performance (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    model_name      VARCHAR(100) NOT NULL,
    model_version   INTEGER NOT NULL,
    evaluation_date DATE NOT NULL,
    metric_name     VARCHAR(50) NOT NULL,
    metric_value    DOUBLE PRECISION NOT NULL,
    sample_size     INTEGER,
    created_at      TIMESTAMP DEFAULT NOW(),
    CONSTRAINT uq_model_performance UNIQUE(model_name, model_version, evaluation_date, metric_name),
    CONSTRAINT chk_metric_name CHECK (metric_name IN (
        'accuracy', 'precision', 'recall', 'f1', 'auc_roc',
        'mae', 'rmse', 'mape', 'r_squared',
        'precision_at_k', 'recall_at_k'
    ))
);

-- Comments for documentation
COMMENT ON TABLE ai_model_performance IS 'Time-series tracking of model performance metrics for quality monitoring';
COMMENT ON COLUMN ai_model_performance.metric_name IS 'Metric type: accuracy/precision/recall for classification, mae/rmse/mape for regression';
COMMENT ON COLUMN ai_model_performance.evaluation_date IS 'Date of evaluation (not timestamp) for daily aggregation';
COMMENT ON COLUMN ai_model_performance.sample_size IS 'Number of samples used in this evaluation';
COMMENT ON CONSTRAINT uq_model_performance ON ai_model_performance IS 'Prevents duplicate metrics for same model/version/date';

-- Indexes for performance tracking queries
CREATE INDEX IF NOT EXISTS idx_model_performance_lookup
    ON ai_model_performance(model_name, model_version, evaluation_date);

CREATE INDEX IF NOT EXISTS idx_model_performance_metric
    ON ai_model_performance(metric_name);

CREATE INDEX IF NOT EXISTS idx_model_performance_date
    ON ai_model_performance(evaluation_date DESC);

CREATE INDEX IF NOT EXISTS idx_model_performance_name_metric
    ON ai_model_performance(model_name, metric_name, evaluation_date DESC);
