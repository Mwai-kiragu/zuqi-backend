-- ===========================================================================================
-- V144__create_ai_supplier_risk_scores.sql
-- Part of Phase 2 — AI Supplier Risk Scoring
-- Author: Zuqi Engineering
-- Date: 2026-03-18
-- ===========================================================================================

CREATE TABLE IF NOT EXISTS ai_supplier_risk_scores (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    distributor_id              UUID NOT NULL,
    supplier_id                 UUID NOT NULL,
    risk_score                  DOUBLE PRECISION,
    risk_tier                   VARCHAR(20),
    delivery_reliability_score  DOUBLE PRECISION,
    quality_score               DOUBLE PRECISION,
    price_consistency_score     DOUBLE PRECISION,
    responsiveness_score        DOUBLE PRECISION,
    data_phase                  VARCHAR(20),
    computed_at                 TIMESTAMP DEFAULT NOW(),

    CONSTRAINT fk_ai_supplier_risk_scores_distributor FOREIGN KEY (distributor_id) REFERENCES distributors(id),
    CONSTRAINT fk_ai_supplier_risk_scores_supplier    FOREIGN KEY (supplier_id)    REFERENCES suppliers(id),
    CONSTRAINT uq_ai_supplier_risk_scores_distributor_supplier UNIQUE (distributor_id, supplier_id)
);

COMMENT ON TABLE ai_supplier_risk_scores IS 'Composite supplier risk scores computed from delivery, quality, pricing and responsiveness signals';
COMMENT ON COLUMN ai_supplier_risk_scores.risk_score                 IS 'Overall risk score [0.0, 100.0]; higher = higher risk';
COMMENT ON COLUMN ai_supplier_risk_scores.risk_tier                  IS 'Risk tier label: CRITICAL, HIGH, MEDIUM, LOW, SAFE';
COMMENT ON COLUMN ai_supplier_risk_scores.delivery_reliability_score IS 'Sub-score based on on-time and complete delivery history [0.0, 100.0]';
COMMENT ON COLUMN ai_supplier_risk_scores.quality_score              IS 'Sub-score based on goods rejection and quality complaint rate [0.0, 100.0]';
COMMENT ON COLUMN ai_supplier_risk_scores.price_consistency_score    IS 'Sub-score based on price volatility across purchase orders [0.0, 100.0]';
COMMENT ON COLUMN ai_supplier_risk_scores.responsiveness_score       IS 'Sub-score based on lead time and communication responsiveness [0.0, 100.0]';
COMMENT ON COLUMN ai_supplier_risk_scores.data_phase                 IS 'Data phase at compute time: SYNTHETIC, TRANSITION, REAL';

CREATE INDEX IF NOT EXISTS idx_ai_supplier_risk_scores_distributor_tier
    ON ai_supplier_risk_scores (distributor_id, risk_tier);

CREATE INDEX IF NOT EXISTS idx_ai_supplier_risk_scores_distributor_score_asc
    ON ai_supplier_risk_scores (distributor_id, risk_score ASC);

CREATE INDEX IF NOT EXISTS idx_ai_supplier_risk_scores_supplier
    ON ai_supplier_risk_scores (supplier_id);

CREATE INDEX IF NOT EXISTS idx_ai_supplier_risk_scores_distributor_computed
    ON ai_supplier_risk_scores (distributor_id, computed_at DESC);
