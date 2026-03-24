-- ===========================================================================================
-- V145__create_ai_price_trends.sql
-- Part of Phase 2 — AI Supplier-Product Price Trend Analysis
-- Author: Zuqi Engineering
-- Date: 2026-03-18
-- ===========================================================================================

CREATE TABLE IF NOT EXISTS ai_price_trends (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    distributor_id        UUID NOT NULL,
    supplier_id           UUID NOT NULL,
    product_id            UUID NOT NULL,
    trend_direction       VARCHAR(20),
    trend_slope           DOUBLE PRECISION,
    pct_change_3m         DOUBLE PRECISION,
    current_unit_price    DOUBLE PRECISION,
    market_avg_price      DOUBLE PRECISION,
    price_volatility      DOUBLE PRECISION,
    computed_at           TIMESTAMP DEFAULT NOW(),

    CONSTRAINT fk_ai_price_trends_distributor FOREIGN KEY (distributor_id) REFERENCES distributors(id),
    CONSTRAINT fk_ai_price_trends_supplier    FOREIGN KEY (supplier_id)    REFERENCES suppliers(id),
    CONSTRAINT fk_ai_price_trends_product     FOREIGN KEY (product_id)     REFERENCES products(id),
    CONSTRAINT uq_ai_price_trends_distributor_supplier_product UNIQUE (distributor_id, supplier_id, product_id)
);

COMMENT ON TABLE ai_price_trends IS 'Statistical price trend analysis per supplier-product pair using linear regression on PO history';
COMMENT ON COLUMN ai_price_trends.trend_direction    IS 'Direction label: RISING, FALLING, STABLE, VOLATILE';
COMMENT ON COLUMN ai_price_trends.trend_slope        IS 'Linear regression slope of unit price over the analysis window (KES per day)';
COMMENT ON COLUMN ai_price_trends.pct_change_3m      IS 'Percentage price change over the last 3 months';
COMMENT ON COLUMN ai_price_trends.current_unit_price IS 'Most recent unit price from purchase orders (KES)';
COMMENT ON COLUMN ai_price_trends.market_avg_price   IS 'Average unit price across all suppliers for this product (KES)';
COMMENT ON COLUMN ai_price_trends.price_volatility   IS 'Coefficient of variation of unit prices over the analysis window';
COMMENT ON COLUMN ai_price_trends.computed_at        IS 'Timestamp when this trend was last recomputed';

CREATE INDEX IF NOT EXISTS idx_ai_price_trends_distributor_direction
    ON ai_price_trends (distributor_id, trend_direction);

CREATE INDEX IF NOT EXISTS idx_ai_price_trends_supplier_product
    ON ai_price_trends (supplier_id, product_id);

CREATE INDEX IF NOT EXISTS idx_ai_price_trends_distributor_computed
    ON ai_price_trends (distributor_id, computed_at DESC);
