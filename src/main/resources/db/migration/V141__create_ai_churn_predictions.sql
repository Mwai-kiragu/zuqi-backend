-- ===========================================================================================
-- V141__create_ai_churn_predictions.sql
-- Part of Phase 2 — AI Customer Churn Prediction
-- Author: Zuqi Engineering
-- Date: 2026-03-18
-- ===========================================================================================

CREATE TABLE IF NOT EXISTS ai_churn_predictions (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    distributor_id        UUID NOT NULL,
    customer_id           UUID NOT NULL,
    churn_probability     DOUBLE PRECISION,
    risk_tier             VARCHAR(20),
    days_since_last_order INTEGER,
    top_churn_factor      VARCHAR(100),
    recommended_action    TEXT,
    confidence_score      DOUBLE PRECISION,
    data_phase            VARCHAR(20),
    model_version         INTEGER,
    computed_at           TIMESTAMP DEFAULT NOW(),

    CONSTRAINT fk_ai_churn_predictions_distributor FOREIGN KEY (distributor_id) REFERENCES distributors(id),
    CONSTRAINT fk_ai_churn_predictions_customer    FOREIGN KEY (customer_id)    REFERENCES merchants(id),
    CONSTRAINT uq_ai_churn_predictions_distributor_customer UNIQUE (distributor_id, customer_id)
);

COMMENT ON TABLE ai_churn_predictions IS 'Churn risk predictions per customer — updated weekly to identify at-risk customers';
COMMENT ON COLUMN ai_churn_predictions.churn_probability     IS 'Probability [0.0, 1.0] that the customer churns within 30 days';
COMMENT ON COLUMN ai_churn_predictions.risk_tier             IS 'Risk label: CRITICAL, HIGH, MEDIUM, LOW, SAFE';
COMMENT ON COLUMN ai_churn_predictions.days_since_last_order IS 'Number of days since the customer last placed an order';
COMMENT ON COLUMN ai_churn_predictions.top_churn_factor      IS 'Primary driver of churn risk (feature name or business reason)';
COMMENT ON COLUMN ai_churn_predictions.recommended_action    IS 'Actionable recommendation for the sales rep or account manager';
COMMENT ON COLUMN ai_churn_predictions.confidence_score      IS 'Model confidence [0.0, 1.0]; lower in SYNTHETIC phase';
COMMENT ON COLUMN ai_churn_predictions.data_phase            IS 'Data phase at compute time: SYNTHETIC, TRANSITION, REAL';
COMMENT ON COLUMN ai_churn_predictions.model_version         IS 'Version of the churn classification model used';

CREATE INDEX IF NOT EXISTS idx_ai_churn_predictions_distributor_tier
    ON ai_churn_predictions (distributor_id, risk_tier);

CREATE INDEX IF NOT EXISTS idx_ai_churn_predictions_distributor_probability
    ON ai_churn_predictions (distributor_id, churn_probability DESC);

CREATE INDEX IF NOT EXISTS idx_ai_churn_predictions_customer
    ON ai_churn_predictions (customer_id);

CREATE INDEX IF NOT EXISTS idx_ai_churn_predictions_distributor_computed
    ON ai_churn_predictions (distributor_id, computed_at DESC);
