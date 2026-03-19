-- ===========================================================================================
-- V148__create_ai_expiry_risk_scores.sql
-- Part of Phase 2 — Inventory AI: Expiry Risk Prediction (Model #10)
-- Author: Zuqi Engineering
-- Date: 2026-03-18
-- ===========================================================================================

CREATE TABLE IF NOT EXISTS ai_expiry_risk_scores (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    distributor_id              UUID NOT NULL,
    warehouse_id                UUID NOT NULL,
    product_id                  UUID NOT NULL,
    batch_id                    UUID,
    batch_number                VARCHAR(100),
    expiry_date                 DATE NOT NULL,
    days_to_expiry              INTEGER,
    current_stock_qty           DOUBLE PRECISION,
    avg_daily_sales_rate        DOUBLE PRECISION,
    projected_days_to_sell      DOUBLE PRECISION,
    sell_through_probability    DOUBLE PRECISION,
    risk_score                  DOUBLE PRECISION NOT NULL,
    risk_tier                   VARCHAR(20),
    recommended_action          VARCHAR(30),
    discount_suggestion_pct     DOUBLE PRECISION,
    confidence_score            DOUBLE PRECISION,
    data_phase                  VARCHAR(20),
    model_version               INTEGER,
    computed_at                 TIMESTAMP DEFAULT NOW(),
    created_at                  TIMESTAMP DEFAULT NOW(),
    updated_at                  TIMESTAMP DEFAULT NOW(),

    CONSTRAINT fk_ai_expiry_risk_distributor FOREIGN KEY (distributor_id) REFERENCES distributors(id),
    CONSTRAINT fk_ai_expiry_risk_warehouse   FOREIGN KEY (warehouse_id)   REFERENCES warehouses(id),
    CONSTRAINT fk_ai_expiry_risk_product     FOREIGN KEY (product_id)     REFERENCES products(id),
    CONSTRAINT fk_ai_expiry_risk_batch       FOREIGN KEY (batch_id)       REFERENCES product_batches(id)
);

COMMENT ON TABLE ai_expiry_risk_scores IS 'XGBoost-predicted sell-through probability for perishable batches — updated daily';
COMMENT ON COLUMN ai_expiry_risk_scores.sell_through_probability IS 'Predicted probability [0.0, 1.0] that batch sells out before expiry';
COMMENT ON COLUMN ai_expiry_risk_scores.risk_score               IS '1 - sell_through_probability; higher = more at risk';
COMMENT ON COLUMN ai_expiry_risk_scores.risk_tier                IS 'NORMAL (<0.3), MODERATE (0.3–0.6), HIGH (0.6–0.8), CRITICAL (>0.8)';
COMMENT ON COLUMN ai_expiry_risk_scores.recommended_action       IS 'NORMAL, DISCOUNT, REDISTRIBUTE, QUARANTINE';
COMMENT ON COLUMN ai_expiry_risk_scores.discount_suggestion_pct  IS 'Suggested markdown percentage to accelerate sell-through';
COMMENT ON COLUMN ai_expiry_risk_scores.confidence_score         IS 'Model confidence [0.0, 1.0]; reduced in SYNTHETIC data phase';

CREATE INDEX IF NOT EXISTS idx_ai_expiry_risk_distributor_warehouse
    ON ai_expiry_risk_scores (distributor_id, warehouse_id);

CREATE INDEX IF NOT EXISTS idx_ai_expiry_risk_distributor_product
    ON ai_expiry_risk_scores (distributor_id, product_id);

CREATE INDEX IF NOT EXISTS idx_ai_expiry_risk_expiry_date
    ON ai_expiry_risk_scores (expiry_date ASC);

CREATE INDEX IF NOT EXISTS idx_ai_expiry_risk_tier
    ON ai_expiry_risk_scores (distributor_id, risk_tier);

CREATE INDEX IF NOT EXISTS idx_ai_expiry_risk_batch
    ON ai_expiry_risk_scores (batch_id) WHERE batch_id IS NOT NULL;
