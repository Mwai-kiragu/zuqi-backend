-- ===========================================================================================
-- V142__create_ai_product_recommendations.sql
-- Part of Phase 2 — AI Product Cross-Sell Recommendations
-- Author: Zuqi Engineering
-- Date: 2026-03-18
-- ===========================================================================================

CREATE TABLE IF NOT EXISTS ai_product_recommendations (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    distributor_id        UUID NOT NULL,
    customer_id           UUID NOT NULL,
    product_id            UUID NOT NULL,
    recommendation_score  DOUBLE PRECISION,
    reason                TEXT,
    source                VARCHAR(50) DEFAULT 'ASSOCIATION_RULE',
    data_phase            VARCHAR(20),
    created_at            TIMESTAMP DEFAULT NOW(),

    CONSTRAINT fk_ai_product_recommendations_distributor FOREIGN KEY (distributor_id) REFERENCES distributors(id),
    CONSTRAINT fk_ai_product_recommendations_customer    FOREIGN KEY (customer_id)    REFERENCES merchants(id),
    CONSTRAINT fk_ai_product_recommendations_product     FOREIGN KEY (product_id)     REFERENCES products(id)
);

COMMENT ON TABLE ai_product_recommendations IS 'Product cross-sell recommendations based on co-purchase patterns';
COMMENT ON COLUMN ai_product_recommendations.recommendation_score IS 'Relevance score [0.0, 1.0]; higher scores surface first';
COMMENT ON COLUMN ai_product_recommendations.reason               IS 'Human-readable explanation of why this product was recommended';
COMMENT ON COLUMN ai_product_recommendations.source               IS 'Algorithm source: ASSOCIATION_RULE, COLLABORATIVE_FILTER, LLM, MANUAL';
COMMENT ON COLUMN ai_product_recommendations.data_phase           IS 'Data phase at compute time: SYNTHETIC, TRANSITION, REAL';

CREATE INDEX IF NOT EXISTS idx_ai_product_recommendations_distributor_customer_score
    ON ai_product_recommendations (distributor_id, customer_id, recommendation_score DESC);

CREATE INDEX IF NOT EXISTS idx_ai_product_recommendations_customer
    ON ai_product_recommendations (customer_id);

CREATE INDEX IF NOT EXISTS idx_ai_product_recommendations_product
    ON ai_product_recommendations (product_id);

CREATE INDEX IF NOT EXISTS idx_ai_product_recommendations_distributor_created
    ON ai_product_recommendations (distributor_id, created_at DESC);
