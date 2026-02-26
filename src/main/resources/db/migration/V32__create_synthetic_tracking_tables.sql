-- Synthetic Data Tracking Tables
-- Phase tracking and generation run audit log for the SYNTHETIC → HYBRID → REAL transition
-- Architecture: synthetic data lives entirely in memory; only metadata is persisted here
-- Part of Phase 1.5: Synthetic Data Infrastructure

CREATE TABLE IF NOT EXISTS ai_data_phase (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    model_name           VARCHAR(100) NOT NULL,
    distributor_id       UUID,
    current_phase        VARCHAR(20)  NOT NULL DEFAULT 'SYNTHETIC',
    real_data_count      INTEGER      NOT NULL DEFAULT 0,
    synthetic_data_count INTEGER      NOT NULL DEFAULT 0,
    real_data_ratio      DECIMAL(5,4) NOT NULL DEFAULT 0.0,
    last_evaluated_at    TIMESTAMP,
    transitioned_at      TIMESTAMP,
    created_at           TIMESTAMP    DEFAULT NOW(),
    updated_at           TIMESTAMP    DEFAULT NOW(),
    CONSTRAINT fk_data_phase_distributor FOREIGN KEY (distributor_id)
        REFERENCES distributors(id) ON DELETE CASCADE,
    CONSTRAINT uq_data_phase_model_distributor UNIQUE (model_name, distributor_id),
    CONSTRAINT chk_data_phase CHECK (current_phase IN ('SYNTHETIC', 'HYBRID', 'REAL')),
    CONSTRAINT chk_real_data_ratio CHECK (real_data_ratio >= 0.0 AND real_data_ratio <= 1.0)
);

-- Comments for documentation
COMMENT ON TABLE  ai_data_phase IS 'Tracks training data maturity phase per model. Drives SYNTHETIC → HYBRID → REAL transition.';
COMMENT ON COLUMN ai_data_phase.current_phase    IS 'SYNTHETIC: <10% real data, HYBRID: 10–80% real, REAL: >80% real';
COMMENT ON COLUMN ai_data_phase.real_data_ratio  IS 'Fraction of real records vs total (real + synthetic). Range 0.0–1.0.';
COMMENT ON COLUMN ai_data_phase.distributor_id   IS 'NULL = global model shared across all distributors';
COMMENT ON COLUMN ai_data_phase.transitioned_at  IS 'Timestamp of most recent phase transition (e.g. SYNTHETIC → HYBRID)';
COMMENT ON CONSTRAINT uq_data_phase_model_distributor ON ai_data_phase IS 'One phase record per model per distributor';

-- Indexes for data phase lookups
CREATE INDEX IF NOT EXISTS idx_data_phase_model
    ON ai_data_phase(model_name);

CREATE INDEX IF NOT EXISTS idx_data_phase_distributor
    ON ai_data_phase(distributor_id) WHERE distributor_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_data_phase_phase
    ON ai_data_phase(current_phase);


CREATE TABLE IF NOT EXISTS ai_synthetic_runs (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    distributor_id    UUID,
    run_type          VARCHAR(30)  NOT NULL,
    random_seed       BIGINT,
    merchant_count    INTEGER,
    history_months    INTEGER,
    archetype_ratios  JSONB,
    config_snapshot   JSONB,
    records_generated JSONB,
    duration_ms       BIGINT,
    status            VARCHAR(20)  NOT NULL DEFAULT 'RUNNING',
    error_message     TEXT,
    triggered_by      VARCHAR(100),
    started_at        TIMESTAMP    DEFAULT NOW(),
    completed_at      TIMESTAMP,
    CONSTRAINT fk_synthetic_run_distributor FOREIGN KEY (distributor_id)
        REFERENCES distributors(id) ON DELETE SET NULL,
    CONSTRAINT chk_synthetic_run_type   CHECK (run_type IN ('FULL_SEED', 'INCREMENTAL', 'RETRAIN')),
    CONSTRAINT chk_synthetic_run_status CHECK (status   IN ('RUNNING', 'COMPLETED', 'FAILED'))
);

-- Comments for documentation
COMMENT ON TABLE  ai_synthetic_runs IS 'Immutable audit log of every synthetic data generation run. Required for KCB compliance.';
COMMENT ON COLUMN ai_synthetic_runs.random_seed       IS 'Seeded RNG value — allows exact regeneration of the same dataset';
COMMENT ON COLUMN ai_synthetic_runs.archetype_ratios  IS 'JSON: {"STEADY_GROWER":0.35,"STABLE_PERFORMER":0.25,...}';
COMMENT ON COLUMN ai_synthetic_runs.config_snapshot   IS 'Full SyntheticDataConfig at time of run for reproducibility';
COMMENT ON COLUMN ai_synthetic_runs.records_generated IS 'JSON: {"merchants":500,"orders":12400,"payments":9800,...}';
COMMENT ON COLUMN ai_synthetic_runs.distributor_id    IS 'NULL = global run across all distributors';

-- Indexes for synthetic run queries
CREATE INDEX IF NOT EXISTS idx_synthetic_runs_distributor
    ON ai_synthetic_runs(distributor_id);

CREATE INDEX IF NOT EXISTS idx_synthetic_runs_status
    ON ai_synthetic_runs(status);

CREATE INDEX IF NOT EXISTS idx_synthetic_runs_started_at
    ON ai_synthetic_runs(started_at DESC);

CREATE INDEX IF NOT EXISTS idx_synthetic_runs_type
    ON ai_synthetic_runs(run_type);

CREATE INDEX IF NOT EXISTS idx_synthetic_runs_completed
    ON ai_synthetic_runs(distributor_id, completed_at DESC) WHERE status = 'COMPLETED';
