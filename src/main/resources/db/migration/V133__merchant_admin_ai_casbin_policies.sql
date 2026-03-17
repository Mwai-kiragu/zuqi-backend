-- ===================================================
-- Casbin RBAC Policies — MERCHANT_ADMIN AI Module Access
-- Mirrors DISTRIBUTOR_ADMIN access for all AI endpoints
-- Date: 2026-03-17
-- ===================================================

-- AI Anomaly Detection
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
('p', 'MERCHANT_ADMIN', '/v1/ai/anomaly/alerts',         'GET'),
('p', 'MERCHANT_ADMIN', '/v1/ai/anomaly/alerts/:id',     'GET|PUT'),
('p', 'MERCHANT_ADMIN', '/v1/ai/anomaly/alerts/summary', 'GET')
ON CONFLICT DO NOTHING;

-- AI Credit Scoring
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
('p', 'MERCHANT_ADMIN', '/v1/ai/credit/evaluate/:id',    'POST'),
('p', 'MERCHANT_ADMIN', '/v1/ai/credit/evaluations/:id', 'GET'),
('p', 'MERCHANT_ADMIN', '/v1/ai/credit/score/:id',       'GET'),
('p', 'MERCHANT_ADMIN', '/v1/ai/credit/adjust/:id',      'POST')
ON CONFLICT DO NOTHING;

-- AI Demand Forecasting
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
('p', 'MERCHANT_ADMIN', '/v1/ai/demand/forecast/:id',           'GET'),
('p', 'MERCHANT_ADMIN', '/v1/ai/demand/forecast/warehouse/:id', 'GET'),
('p', 'MERCHANT_ADMIN', '/v1/ai/demand/suggestions/:id',        'GET')
ON CONFLICT DO NOTHING;

-- AI Predictions
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
('p', 'MERCHANT_ADMIN', '/v1/ai/prediction/stockout/:id',        'GET'),
('p', 'MERCHANT_ADMIN', '/v1/ai/prediction/rep-performance',     'GET'),
('p', 'MERCHANT_ADMIN', '/v1/ai/prediction/rep-performance/:id', 'GET')
ON CONFLICT DO NOTHING;

-- AI Route Optimization
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
('p', 'MERCHANT_ADMIN', '/v1/ai/routing/optimize',     'POST'),
('p', 'MERCHANT_ADMIN', '/v1/ai/routing/reoptimize',   'POST'),
('p', 'MERCHANT_ADMIN', '/v1/ai/routing/routes/:date', 'GET'),
('p', 'MERCHANT_ADMIN', '/v1/ai/routing/routes/:id',   'GET')
ON CONFLICT DO NOTHING;

-- AI Recommendations
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
('p', 'MERCHANT_ADMIN', '/v1/ai/recommendations/:distributorId',          'GET'),
('p', 'MERCHANT_ADMIN', '/v1/ai/recommendations/:distributorId/generate', 'POST'),
('p', 'MERCHANT_ADMIN', '/v1/ai/recommendations/single/:id',              'GET'),
('p', 'MERCHANT_ADMIN', '/v1/ai/recommendations/:id',                     'GET'),
('p', 'MERCHANT_ADMIN', '/v1/ai/recommendations/:id/accept',              'PUT'),
('p', 'MERCHANT_ADMIN', '/v1/ai/recommendations/:id/reject',              'PUT')
ON CONFLICT DO NOTHING;

-- AI Compliance Reports
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
('p', 'MERCHANT_ADMIN', '/v1/ai/reports/compliance/generate',  'POST'),
('p', 'MERCHANT_ADMIN', '/v1/ai/reports/compliance/templates', 'GET'),
('p', 'MERCHANT_ADMIN', '/v1/ai/reports/compliance/:id',       'GET')
ON CONFLICT DO NOTHING;

-- AI Assistant Report (upgrade from V115 — add report access)
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
('p', 'MERCHANT_ADMIN', '/v1/ai/assistant/report', 'POST')
ON CONFLICT DO NOTHING;
