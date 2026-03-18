-- ===========================================================================================
-- V146__create_ai_pricing_recommendations.sql
-- Part of Phase 2 — AI Smart Pricing Recommendations
-- Author: Zuqi Engineering
-- Date: 2026-03-18
-- ===========================================================================================

CREATE TABLE IF NOT EXISTS ai_pricing_recommendations (
    id                                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    distributor_id                      UUID NOT NULL,
    product_id                          UUID NOT NULL,
    current_price                       DOUBLE PRECISION,
    recommended_price                   DOUBLE PRECISION,
    price_change_pct                    DOUBLE PRECISION,
    predicted_demand_at_current         DOUBLE PRECISION,
    predicted_demand_at_recommended     DOUBLE PRECISION,
    estimated_revenue_impact_kes        DOUBLE PRECISION,
    reason                              TEXT,
    confidence_score                    DOUBLE PRECISION,
    data_phase                          VARCHAR(20),
    status                              VARCHAR(20) DEFAULT 'PENDING',
    model_version                       INTEGER,
    created_at                          TIMESTAMP DEFAULT NOW(),
    updated_at                          TIMESTAMP DEFAULT NOW(),

    CONSTRAINT fk_ai_pricing_recommendations_distributor FOREIGN KEY (distributor_id) REFERENCES distributors(id),
    CONSTRAINT fk_ai_pricing_recommendations_product     FOREIGN KEY (product_id)     REFERENCES products(id)
);

COMMENT ON TABLE ai_pricing_recommendations IS 'Smart pricing recommendations with revenue impact estimates — updated weekly';
COMMENT ON COLUMN ai_pricing_recommendations.current_price                   IS 'Current selling price of the product (KES)';
COMMENT ON COLUMN ai_pricing_recommendations.recommended_price               IS 'AI-recommended selling price (KES)';
COMMENT ON COLUMN ai_pricing_recommendations.price_change_pct                IS 'Percentage change from current to recommended price (positive = increase)';
COMMENT ON COLUMN ai_pricing_recommendations.predicted_demand_at_current     IS 'Predicted unit demand over 30 days at the current price';
COMMENT ON COLUMN ai_pricing_recommendations.predicted_demand_at_recommended IS 'Predicted unit demand over 30 days at the recommended price';
COMMENT ON COLUMN ai_pricing_recommendations.estimated_revenue_impact_kes    IS 'Estimated net revenue delta (KES) from applying the recommendation';
COMMENT ON COLUMN ai_pricing_recommendations.reason                          IS 'Narrative explanation of the pricing rationale';
COMMENT ON COLUMN ai_pricing_recommendations.confidence_score                IS 'Model confidence [0.0, 1.0]; reduced in SYNTHETIC data phase';
COMMENT ON COLUMN ai_pricing_recommendations.data_phase                      IS 'Data phase at compute time: SYNTHETIC, TRANSITION, REAL';
COMMENT ON COLUMN ai_pricing_recommendations.status                          IS 'Workflow status: PENDING, ACCEPTED, REJECTED, APPLIED, EXPIRED';
COMMENT ON COLUMN ai_pricing_recommendations.model_version                   IS 'Version of the pricing optimisation model used';

CREATE INDEX IF NOT EXISTS idx_ai_pricing_recommendations_distributor_product_created
    ON ai_pricing_recommendations (distributor_id, product_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_ai_pricing_recommendations_distributor_status
    ON ai_pricing_recommendations (distributor_id, status);

CREATE INDEX IF NOT EXISTS idx_ai_pricing_recommendations_product
    ON ai_pricing_recommendations (product_id);

CREATE INDEX IF NOT EXISTS idx_ai_pricing_recommendations_distributor_revenue_impact
    ON ai_pricing_recommendations (distributor_id, estimated_revenue_impact_kes DESC);
