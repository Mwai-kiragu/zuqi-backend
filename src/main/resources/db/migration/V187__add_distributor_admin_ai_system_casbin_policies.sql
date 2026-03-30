-- ===================================================
-- Casbin RBAC Policies — DISTRIBUTOR_ADMIN AI System Health
-- Adds read-only access to AI system health endpoints
-- ===================================================

INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
('p', 'DISTRIBUTOR_ADMIN', '/v1/ai/system/health',                   'GET'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/ai/system/models',                   'GET'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/ai/system/models/:name',             'GET'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/ai/system/models/:name/performance', 'GET')
ON CONFLICT DO NOTHING;
