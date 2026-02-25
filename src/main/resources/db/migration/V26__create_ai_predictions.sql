-- AI Predictions Audit Log
-- Stores every prediction made by AI models for compliance and audit
-- Part of Phase 1: Foundation Infrastructure

CREATE TABLE IF NOT EXISTS ai_predictions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    model_name      VARCHAR(100) NOT NULL,
    model_version   INTEGER NOT NULL,
    entity_type     VARCHAR(50) NOT NULL,
    entity_id       UUID NOT NULL,
    distributor_id  UUID NOT NULL,
    input_features_hash VARCHAR(64),
    prediction_value    JSONB NOT NULL,
    confidence_score    DOUBLE PRECISION,
    was_overridden      BOOLEAN DEFAULT FALSE,
    override_value      JSONB,
    override_by         VARCHAR(100),
    override_reason     TEXT,
    expires_at      TIMESTAMP,
    created_at      TIMESTAMP DEFAULT NOW(),
    CONSTRAINT fk_predictions_distributor FOREIGN KEY (distributor_id)
        REFERENCES distributors(id) ON DELETE CASCADE,
    CONSTRAINT chk_entity_type CHECK (entity_type IN ('merchant', 'warehouse_sku', 'sales_rep', 'payment', 'order', 'route'))
);

-- Comments for documentation
COMMENT ON TABLE ai_predictions IS 'Complete audit trail of all AI predictions for compliance (KCB requirement)';
COMMENT ON COLUMN ai_predictions.input_features_hash IS 'SHA-256 hash of input features for deduplication and verification';
COMMENT ON COLUMN ai_predictions.prediction_value IS 'JSON: {"grade": "B", "limit": 85000} or {"quantity": 24}';
COMMENT ON COLUMN ai_predictions.was_overridden IS 'TRUE if human overrode the AI prediction';
COMMENT ON COLUMN ai_predictions.expires_at IS 'Data retention/GDPR compliance - NULL means keep forever';
COMMENT ON COLUMN ai_predictions.entity_type IS 'Type of entity being predicted: merchant, warehouse_sku, sales_rep, etc.';

-- Indexes for prediction lookups
CREATE INDEX IF NOT EXISTS idx_predictions_entity
    ON ai_predictions(entity_type, entity_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_predictions_model
    ON ai_predictions(model_name, model_version);

CREATE INDEX IF NOT EXISTS idx_predictions_distributor
    ON ai_predictions(distributor_id);

CREATE INDEX IF NOT EXISTS idx_predictions_created
    ON ai_predictions(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_predictions_overridden
    ON ai_predictions(was_overridden) WHERE was_overridden = TRUE;

CREATE INDEX IF NOT EXISTS idx_predictions_expires
    ON ai_predictions(expires_at) WHERE expires_at IS NOT NULL;
