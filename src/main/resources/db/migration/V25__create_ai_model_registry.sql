-- AI Model Registry Table
-- Stores ML model metadata, versioning, binaries, and performance metrics
-- Part of Phase 1: Foundation Infrastructure

CREATE TABLE IF NOT EXISTS ai_model_registry (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    model_name      VARCHAR(100) NOT NULL,
    model_version   INTEGER NOT NULL,
    algorithm       VARCHAR(50) NOT NULL,
    status          VARCHAR(20) NOT NULL,
    distributor_id  UUID,
    training_data_start TIMESTAMP,
    training_data_end   TIMESTAMP,
    training_record_count INTEGER,
    performance_metrics JSONB,
    hyperparameters     JSONB,
    model_binary        BYTEA,
    model_size_bytes    BIGINT,
    feature_columns     JSONB,
    created_at      TIMESTAMP DEFAULT NOW(),
    created_by      VARCHAR(100),
    promoted_at     TIMESTAMP,
    retired_at      TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT NOW(),
    CONSTRAINT fk_model_registry_distributor FOREIGN KEY (distributor_id)
        REFERENCES distributors(id) ON DELETE CASCADE,
    CONSTRAINT uq_model_name_version UNIQUE(model_name, model_version),
    CONSTRAINT chk_model_status CHECK (status IN ('TRAINING', 'EVALUATING', 'ACTIVE', 'RETIRED'))
);

-- Comments for documentation
COMMENT ON TABLE ai_model_registry IS 'ML model lifecycle management - versioning, metadata, and binaries';
COMMENT ON COLUMN ai_model_registry.status IS 'Model lifecycle: TRAINING -> EVALUATING -> ACTIVE -> RETIRED';
COMMENT ON COLUMN ai_model_registry.distributor_id IS 'Optional: for distributor-specific models (NULL = global)';
COMMENT ON COLUMN ai_model_registry.performance_metrics IS 'JSON: {"mae": 2.3, "rmse": 4.1, "mape": 0.12}';
COMMENT ON COLUMN ai_model_registry.hyperparameters IS 'JSON: {"max_depth": 6, "learning_rate": 0.1}';
COMMENT ON COLUMN ai_model_registry.feature_columns IS 'JSON: Ordered list of feature names for consistency';

-- Indexes for quick model lookup
CREATE INDEX IF NOT EXISTS idx_model_active
    ON ai_model_registry(model_name, status) WHERE status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_model_registry_created
    ON ai_model_registry(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_model_registry_distributor
    ON ai_model_registry(distributor_id) WHERE distributor_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_model_registry_name
    ON ai_model_registry(model_name);
