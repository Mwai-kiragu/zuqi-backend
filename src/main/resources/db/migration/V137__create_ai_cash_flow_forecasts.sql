-- ===========================================================================================
-- V137__create_ai_cash_flow_forecasts.sql
-- Part of Phase 2 — AI Cash Flow Forecasting
-- Author: Zuqi Engineering
-- Date: 2026-03-18
-- ===========================================================================================

CREATE TABLE IF NOT EXISTS ai_cash_flow_forecasts (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    distributor_id      UUID NOT NULL,
    forecast_date       DATE NOT NULL,
    predicted_inflow    DOUBLE PRECISION,
    predicted_outflow   DOUBLE PRECISION,
    predicted_net       DOUBLE PRECISION,
    lower_bound_net     DOUBLE PRECISION,
    upper_bound_net     DOUBLE PRECISION,
    confidence_score    DOUBLE PRECISION,
    data_phase          VARCHAR(20),
    model_version       INTEGER,
    created_at          TIMESTAMP DEFAULT NOW(),

    CONSTRAINT fk_ai_cash_flow_forecasts_distributor FOREIGN KEY (distributor_id) REFERENCES distributors(id),
    CONSTRAINT uq_ai_cash_flow_forecasts_distributor_date UNIQUE (distributor_id, forecast_date)
);

COMMENT ON TABLE ai_cash_flow_forecasts IS 'Daily cash flow predictions with 7/30/90 day horizons per distributor';
COMMENT ON COLUMN ai_cash_flow_forecasts.forecast_date      IS 'Calendar date this forecast applies to';
COMMENT ON COLUMN ai_cash_flow_forecasts.predicted_inflow   IS 'Predicted incoming cash (KES) for the date';
COMMENT ON COLUMN ai_cash_flow_forecasts.predicted_outflow  IS 'Predicted outgoing cash (KES) for the date';
COMMENT ON COLUMN ai_cash_flow_forecasts.predicted_net      IS 'Net predicted cash position (inflow − outflow)';
COMMENT ON COLUMN ai_cash_flow_forecasts.lower_bound_net    IS 'Lower confidence interval bound for net cash';
COMMENT ON COLUMN ai_cash_flow_forecasts.upper_bound_net    IS 'Upper confidence interval bound for net cash';
COMMENT ON COLUMN ai_cash_flow_forecasts.confidence_score   IS 'Model confidence [0.0, 1.0]; lower in SYNTHETIC phase';
COMMENT ON COLUMN ai_cash_flow_forecasts.data_phase         IS 'Data phase at time of prediction: SYNTHETIC, TRANSITION, REAL';
COMMENT ON COLUMN ai_cash_flow_forecasts.model_version      IS 'Version of the forecasting model used';

CREATE INDEX IF NOT EXISTS idx_ai_cash_flow_forecasts_distributor_date
    ON ai_cash_flow_forecasts (distributor_id, forecast_date DESC);

CREATE INDEX IF NOT EXISTS idx_ai_cash_flow_forecasts_date
    ON ai_cash_flow_forecasts (forecast_date DESC);

CREATE INDEX IF NOT EXISTS idx_ai_cash_flow_forecasts_distributor_phase
    ON ai_cash_flow_forecasts (distributor_id, data_phase);
