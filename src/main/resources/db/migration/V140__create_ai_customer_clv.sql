-- ===========================================================================================
-- V140__create_ai_customer_clv.sql
-- Part of Phase 2 — AI Customer Lifetime Value Predictions
-- Author: Zuqi Engineering
-- Date: 2026-03-18
-- ===========================================================================================

CREATE TABLE IF NOT EXISTS ai_customer_clv (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    distributor_id          UUID NOT NULL,
    customer_id             UUID NOT NULL,
    predicted_revenue_12m   DOUBLE PRECISION,
    lower_bound             DOUBLE PRECISION,
    upper_bound             DOUBLE PRECISION,
    confidence_score        DOUBLE PRECISION,
    data_phase              VARCHAR(20),
    model_version           INTEGER,
    computed_at             TIMESTAMP DEFAULT NOW(),

    CONSTRAINT fk_ai_customer_clv_distributor FOREIGN KEY (distributor_id) REFERENCES distributors(id),
    CONSTRAINT fk_ai_customer_clv_customer    FOREIGN KEY (customer_id)    REFERENCES merchants(id),
    CONSTRAINT uq_ai_customer_clv_distributor_customer UNIQUE (distributor_id, customer_id)
);

COMMENT ON TABLE ai_customer_clv IS 'Customer Lifetime Value predictions — 12-month revenue forecast per customer';
COMMENT ON COLUMN ai_customer_clv.predicted_revenue_12m IS 'Predicted total revenue (KES) from this customer over the next 12 months';
COMMENT ON COLUMN ai_customer_clv.lower_bound           IS 'Lower confidence interval bound for the 12-month revenue prediction';
COMMENT ON COLUMN ai_customer_clv.upper_bound           IS 'Upper confidence interval bound for the 12-month revenue prediction';
COMMENT ON COLUMN ai_customer_clv.confidence_score      IS 'Model confidence [0.0, 1.0]; reduced in SYNTHETIC data phase';
COMMENT ON COLUMN ai_customer_clv.data_phase            IS 'Data phase at compute time: SYNTHETIC, TRANSITION, REAL';
COMMENT ON COLUMN ai_customer_clv.model_version         IS 'Version of the CLV regression model used for this prediction';

CREATE INDEX IF NOT EXISTS idx_ai_customer_clv_distributor_revenue
    ON ai_customer_clv (distributor_id, predicted_revenue_12m DESC);

CREATE INDEX IF NOT EXISTS idx_ai_customer_clv_customer
    ON ai_customer_clv (customer_id);

CREATE INDEX IF NOT EXISTS idx_ai_customer_clv_distributor_computed
    ON ai_customer_clv (distributor_id, computed_at DESC);
