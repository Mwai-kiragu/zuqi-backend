-- Extend ai_model_registry with synthetic data phase tracking columns
-- Records data composition at time of training for full model provenance
-- Part of Phase 1.5: Synthetic Data Infrastructure

ALTER TABLE ai_model_registry ADD COLUMN IF NOT EXISTS data_phase             VARCHAR(20);
ALTER TABLE ai_model_registry ADD COLUMN IF NOT EXISTS real_data_ratio        DECIMAL(5,4);
ALTER TABLE ai_model_registry ADD COLUMN IF NOT EXISTS synthetic_records_used INTEGER;
ALTER TABLE ai_model_registry ADD COLUMN IF NOT EXISTS real_records_used      INTEGER;

-- Comments for documentation
COMMENT ON COLUMN ai_model_registry.data_phase             IS 'Data phase at time of training: SYNTHETIC / HYBRID / REAL';
COMMENT ON COLUMN ai_model_registry.real_data_ratio        IS 'Fraction of real records used in training (0.0–1.0)';
COMMENT ON COLUMN ai_model_registry.synthetic_records_used IS 'Count of synthetic training records used';
COMMENT ON COLUMN ai_model_registry.real_records_used      IS 'Count of real training records used';
