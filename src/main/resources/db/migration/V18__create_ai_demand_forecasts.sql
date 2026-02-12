-- AI Demand Forecasts
-- Stores pre-computed demand predictions for merchant-SKU combinations
-- Consumed by stockout prediction and order suggestions
-- Part of Phase 3: Demand Forecasting Module

CREATE TABLE IF NOT EXISTS ai_demand_forecasts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id     UUID NOT NULL,
    sku_id          UUID NOT NULL,
    distributor_id  UUID NOT NULL,
    forecast_date   DATE NOT NULL,
    predicted_qty   DOUBLE PRECISION NOT NULL,
    confidence_lower DOUBLE PRECISION,
    confidence_upper DOUBLE PRECISION,
    model_version   INTEGER NOT NULL,
    expires_at      TIMESTAMP,
    created_at      TIMESTAMP DEFAULT NOW(),
    CONSTRAINT fk_demand_merchant FOREIGN KEY (merchant_id)
        REFERENCES merchants(id) ON DELETE CASCADE,
    CONSTRAINT fk_demand_sku FOREIGN KEY (sku_id)
        REFERENCES products(id) ON DELETE CASCADE,
    CONSTRAINT fk_demand_distributor FOREIGN KEY (distributor_id)
        REFERENCES distributors(id) ON DELETE CASCADE,
    CONSTRAINT uq_demand_forecast UNIQUE(merchant_id, sku_id, forecast_date)
);

-- Comments for documentation
COMMENT ON TABLE ai_demand_forecasts IS 'Pre-computed demand predictions generated nightly for all merchant-SKU combinations';
COMMENT ON COLUMN ai_demand_forecasts.predicted_qty IS 'Predicted order quantity for this merchant-SKU on forecast_date';
COMMENT ON COLUMN ai_demand_forecasts.confidence_lower IS 'Lower bound of 95% confidence interval';
COMMENT ON COLUMN ai_demand_forecasts.confidence_upper IS 'Upper bound of 95% confidence interval';
COMMENT ON COLUMN ai_demand_forecasts.model_version IS 'Version of demand forecasting model used';
COMMENT ON COLUMN ai_demand_forecasts.expires_at IS 'Forecasts older than 30 days can be purged';
COMMENT ON CONSTRAINT uq_demand_forecast ON ai_demand_forecasts IS 'One forecast per merchant-SKU-date combination';

-- Indexes for forecast lookups
CREATE INDEX IF NOT EXISTS idx_demand_forecasts_merchant
    ON ai_demand_forecasts(merchant_id, forecast_date);

CREATE INDEX IF NOT EXISTS idx_demand_forecasts_sku
    ON ai_demand_forecasts(sku_id, forecast_date);

CREATE INDEX IF NOT EXISTS idx_demand_forecasts_date
    ON ai_demand_forecasts(forecast_date DESC);

CREATE INDEX IF NOT EXISTS idx_demand_forecasts_distributor
    ON ai_demand_forecasts(distributor_id, forecast_date);

CREATE INDEX IF NOT EXISTS idx_demand_forecasts_expires
    ON ai_demand_forecasts(expires_at) WHERE expires_at IS NOT NULL;
