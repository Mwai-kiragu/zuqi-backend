-- ===========================================================================================
-- V143__create_ai_visit_recommendations.sql
-- Part of Phase 2 — AI Sales Rep Visit Scheduling Recommendations
-- Author: Zuqi Engineering
-- Date: 2026-03-18
-- ===========================================================================================

CREATE TABLE IF NOT EXISTS ai_visit_recommendations (
    id                              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    distributor_id                  UUID NOT NULL,
    sales_rep_id                    UUID NOT NULL,
    customer_id                     UUID NOT NULL,
    recommended_day                 INTEGER,
    predicted_conversion            DOUBLE PRECISION,
    recommended_frequency_per_week  DOUBLE PRECISION,
    reason                          TEXT,
    data_phase                      VARCHAR(20),
    model_version                   INTEGER,
    created_at                      TIMESTAMP DEFAULT NOW(),

    CONSTRAINT fk_ai_visit_recommendations_distributor FOREIGN KEY (distributor_id) REFERENCES distributors(id),
    CONSTRAINT fk_ai_visit_recommendations_sales_rep   FOREIGN KEY (sales_rep_id)   REFERENCES users(id),
    CONSTRAINT fk_ai_visit_recommendations_customer    FOREIGN KEY (customer_id)    REFERENCES merchants(id),
    CONSTRAINT uq_ai_visit_recommendations_rep_customer UNIQUE (distributor_id, sales_rep_id, customer_id)
);

COMMENT ON TABLE ai_visit_recommendations IS 'AI-optimized visit day and frequency recommendations for sales reps per customer';
COMMENT ON COLUMN ai_visit_recommendations.recommended_day                IS 'ISO day of week for optimal visit (1=Monday … 7=Sunday)';
COMMENT ON COLUMN ai_visit_recommendations.predicted_conversion           IS 'Predicted order conversion probability [0.0, 1.0] for the recommended day';
COMMENT ON COLUMN ai_visit_recommendations.recommended_frequency_per_week IS 'Suggested number of visits per week (may be fractional, e.g. 0.5 = fortnightly)';
COMMENT ON COLUMN ai_visit_recommendations.reason                         IS 'Explanation of the recommendation for the sales rep';
COMMENT ON COLUMN ai_visit_recommendations.data_phase                     IS 'Data phase at compute time: SYNTHETIC, TRANSITION, REAL';
COMMENT ON COLUMN ai_visit_recommendations.model_version                  IS 'Version of the visit scheduling model used';

CREATE INDEX IF NOT EXISTS idx_ai_visit_recommendations_rep_conversion
    ON ai_visit_recommendations (sales_rep_id, predicted_conversion DESC);

CREATE INDEX IF NOT EXISTS idx_ai_visit_recommendations_distributor_customer
    ON ai_visit_recommendations (distributor_id, customer_id);

CREATE INDEX IF NOT EXISTS idx_ai_visit_recommendations_distributor_created
    ON ai_visit_recommendations (distributor_id, created_at DESC);
