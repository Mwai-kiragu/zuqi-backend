-- ===================================================
-- Casbin RBAC Policies — All AI Module Endpoints
-- Part of Phase 2-6 AI Integration
-- Author: Zuqi Engineering
-- Date: 2026-03-13
-- ===================================================

-- AI System Health (SUPER_ADMIN only)
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
('p', 'SUPER_ADMIN', '/v1/ai/system/health', 'GET')
ON CONFLICT DO NOTHING;

-- AI Credit Scoring
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
('p', 'DISTRIBUTOR_ADMIN', '/v1/ai/credit/evaluate/:id',    'POST'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/ai/credit/evaluations/:id', 'GET'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/ai/credit/score/:id',       'GET'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/ai/credit/adjust/:id',      'POST'),
('p', 'FINANCE',           '/v1/ai/credit/evaluate/:id',    'POST'),
('p', 'FINANCE',           '/v1/ai/credit/evaluations/:id', 'GET'),
('p', 'FINANCE',           '/v1/ai/credit/score/:id',       'GET'),
('p', 'FINANCE',           '/v1/ai/credit/adjust/:id',      'POST')
ON CONFLICT DO NOTHING;

-- AI Demand Forecasting
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
('p', 'DISTRIBUTOR_ADMIN',  '/v1/ai/demand/forecast/:id',           'GET'),
('p', 'DISTRIBUTOR_ADMIN',  '/v1/ai/demand/forecast/warehouse/:id', 'GET'),
('p', 'DISTRIBUTOR_ADMIN',  '/v1/ai/demand/suggestions/:id',        'GET'),
('p', 'SALES_REP',          '/v1/ai/demand/suggestions/:id',        'GET'),
('p', 'WAREHOUSE_MANAGER',  '/v1/ai/demand/forecast/:id',           'GET'),
('p', 'WAREHOUSE_MANAGER',  '/v1/ai/demand/forecast/warehouse/:id', 'GET')
ON CONFLICT DO NOTHING;

-- AI Anomaly Detection
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
('p', 'DISTRIBUTOR_ADMIN', '/v1/ai/anomaly/alerts',         'GET'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/ai/anomaly/alerts/:id',     'GET|PUT'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/ai/anomaly/alerts/summary', 'GET'),
('p', 'WAREHOUSE_MANAGER', '/v1/ai/anomaly/alerts',         'GET'),
('p', 'WAREHOUSE_MANAGER', '/v1/ai/anomaly/alerts/:id',     'GET|PUT'),
('p', 'WAREHOUSE_MANAGER', '/v1/ai/anomaly/alerts/summary', 'GET'),
('p', 'FINANCE',           '/v1/ai/anomaly/alerts',         'GET'),
('p', 'FINANCE',           '/v1/ai/anomaly/alerts/:id',     'GET|PUT'),
('p', 'FINANCE',           '/v1/ai/anomaly/alerts/summary', 'GET')
ON CONFLICT DO NOTHING;

-- AI Predictions (stockout + rep performance)
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
('p', 'DISTRIBUTOR_ADMIN', '/v1/ai/prediction/stockout/:id',         'GET'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/ai/prediction/rep-performance',      'GET'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/ai/prediction/rep-performance/:id',  'GET'),
('p', 'WAREHOUSE_MANAGER', '/v1/ai/prediction/stockout/:id',         'GET')
ON CONFLICT DO NOTHING;

-- AI Route Optimization
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
('p', 'DISTRIBUTOR_ADMIN', '/v1/ai/routing/optimize',      'POST'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/ai/routing/reoptimize',    'POST'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/ai/routing/routes/:date',  'GET'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/ai/routing/routes/:id',    'GET'),
('p', 'DRIVER',            '/v1/ai/routing/routes/:date',  'GET'),
('p', 'DRIVER',            '/v1/ai/routing/routes/:id',    'GET')
ON CONFLICT DO NOTHING;

-- AI Recommendations
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
('p', 'DISTRIBUTOR_ADMIN', '/v1/ai/recommendations/:distributorId',          'GET'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/ai/recommendations/:distributorId/generate', 'POST'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/ai/recommendations/single/:id',              'GET'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/ai/recommendations/:id',                     'GET'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/ai/recommendations/:id/accept',              'PUT'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/ai/recommendations/:id/reject',              'PUT')
ON CONFLICT DO NOTHING;

-- AI Compliance Reports
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
('p', 'DISTRIBUTOR_ADMIN', '/v1/ai/reports/compliance/generate',   'POST'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/ai/reports/compliance/templates',  'GET'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/ai/reports/compliance/:id',        'GET')
ON CONFLICT DO NOTHING;
