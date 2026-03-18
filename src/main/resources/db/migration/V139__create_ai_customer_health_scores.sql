-- ===========================================================================================
-- V139__create_ai_customer_health_scores.sql
-- Part of Phase 2 — AI Customer Health Scoring
-- Author: Zuqi Engineering
-- Date: 2026-03-18
-- ===========================================================================================

CREATE TABLE IF NOT EXISTS ai_customer_health_scores (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    distributor_id              UUID NOT NULL,
    customer_id                 UUID NOT NULL,
    health_score                DOUBLE PRECISION,
    health_tier                 VARCHAR(30),
    order_frequency_score       DOUBLE PRECISION,
    payment_timeliness_score    DOUBLE PRECISION,
    revenue_trend_score         DOUBLE PRECISION,
    engagement_score            DOUBLE PRECISION,
    credit_health_score         DOUBLE PRECISION,
    data_phase                  VARCHAR(20),
    computed_at                 TIMESTAMP DEFAULT NOW(),

    CONSTRAINT fk_ai_customer_health_scores_distributor FOREIGN KEY (distributor_id) REFERENCES distributors(id),
    CONSTRAINT fk_ai_customer_health_scores_customer    FOREIGN KEY (customer_id)    REFERENCES merchants(id),
    CONSTRAINT uq_ai_customer_health_scores_distributor_customer UNIQUE (distributor_id, customer_id)
);

COMMENT ON TABLE ai_customer_health_scores IS 'Composite customer health scores computed from order, payment, engagement and credit signals';
COMMENT ON COLUMN ai_customer_health_scores.health_score                IS 'Overall composite health score [0.0, 100.0]';
COMMENT ON COLUMN ai_customer_health_scores.health_tier                 IS 'Tier label: EXCELLENT, GOOD, FAIR, POOR, CRITICAL';
COMMENT ON COLUMN ai_customer_health_scores.order_frequency_score       IS 'Sub-score based on ordering regularity [0.0, 100.0]';
COMMENT ON COLUMN ai_customer_health_scores.payment_timeliness_score    IS 'Sub-score based on on-time payment history [0.0, 100.0]';
COMMENT ON COLUMN ai_customer_health_scores.revenue_trend_score         IS 'Sub-score based on revenue growth trajectory [0.0, 100.0]';
COMMENT ON COLUMN ai_customer_health_scores.engagement_score            IS 'Sub-score based on rep visit and interaction activity [0.0, 100.0]';
COMMENT ON COLUMN ai_customer_health_scores.credit_health_score         IS 'Sub-score derived from credit utilisation and repayment [0.0, 100.0]';
COMMENT ON COLUMN ai_customer_health_scores.data_phase                  IS 'Data phase at compute time: SYNTHETIC, TRANSITION, REAL';

CREATE INDEX IF NOT EXISTS idx_ai_customer_health_scores_distributor_tier
    ON ai_customer_health_scores (distributor_id, health_tier);

CREATE INDEX IF NOT EXISTS idx_ai_customer_health_scores_customer
    ON ai_customer_health_scores (customer_id);

CREATE INDEX IF NOT EXISTS idx_ai_customer_health_scores_distributor_score
    ON ai_customer_health_scores (distributor_id, health_score DESC);
