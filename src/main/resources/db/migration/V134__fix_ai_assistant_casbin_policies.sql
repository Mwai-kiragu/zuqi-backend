-- ===================================================
-- Fix AI Assistant Casbin Policies
-- Part of AI Chat Feature — Bug Fix
-- Author: Zuqi Engineering
-- Date: 2026-03-17
--
-- Fixes:
-- 1. conversations path was /v1/ai/assistant/conversations (no :id parameter)
--    but the endpoint is /conversations/{distributorId} -> normalized to
--    /v1/ai/assistant/conversations/:id by CasbinAuthorizationFilter.
--    Fix: use /v1/ai/assistant/conversations/:id in all role policies.
--
-- 2. GET /v1/ai/assistant/report-data had no policy -> blocked for all non-SUPER_ADMIN.
--
-- 3. DELETE /v1/ai/assistant/history/:conversationId had no policy -> blocked.
--
-- 4. WAREHOUSE_MANAGER and DRIVER had no assistant policies at all.
--
-- Note: SUPER_ADMIN is bypassed entirely by CasbinAuthorizationFilter (line 82),
--       so no SUPER_ADMIN rows are needed here.
-- ===================================================

-- Remove old wrong conversations policies
DELETE FROM casbin_rule
WHERE ptype = 'p'
  AND v1 = '/v1/ai/assistant/conversations'
  AND v2 = 'GET';

-- DISTRIBUTOR_ADMIN: full access
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
('p', 'DISTRIBUTOR_ADMIN', '/v1/ai/assistant/conversations/:id', 'GET'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/ai/assistant/report-data',       'GET'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/ai/assistant/history/:id',       'DELETE')
ON CONFLICT DO NOTHING;

-- SALES_REP
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
('p', 'SALES_REP', '/v1/ai/assistant/conversations/:id', 'GET'),
('p', 'SALES_REP', '/v1/ai/assistant/report-data',       'GET'),
('p', 'SALES_REP', '/v1/ai/assistant/history/:id',       'DELETE')
ON CONFLICT DO NOTHING;

-- MERCHANT_ADMIN
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
('p', 'MERCHANT_ADMIN', '/v1/ai/assistant/conversations/:id', 'GET'),
('p', 'MERCHANT_ADMIN', '/v1/ai/assistant/report-data',       'GET'),
('p', 'MERCHANT_ADMIN', '/v1/ai/assistant/history/:id',       'DELETE')
ON CONFLICT DO NOTHING;

-- FINANCE
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
('p', 'FINANCE', '/v1/ai/assistant/conversations/:id', 'GET'),
('p', 'FINANCE', '/v1/ai/assistant/report-data',       'GET'),
('p', 'FINANCE', '/v1/ai/assistant/history/:id',       'DELETE')
ON CONFLICT DO NOTHING;

-- WAREHOUSE_MANAGER: inventory/anomaly/demand focused
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
('p', 'WAREHOUSE_MANAGER', '/v1/ai/assistant/chat',                'POST'),
('p', 'WAREHOUSE_MANAGER', '/v1/ai/assistant/history/:id',         'GET'),
('p', 'WAREHOUSE_MANAGER', '/v1/ai/assistant/history/:id',         'DELETE'),
('p', 'WAREHOUSE_MANAGER', '/v1/ai/assistant/conversations/:id',   'GET'),
('p', 'WAREHOUSE_MANAGER', '/v1/ai/assistant/report-data',         'GET')
ON CONFLICT DO NOTHING;

-- DRIVER: delivery focused
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
('p', 'DRIVER', '/v1/ai/assistant/chat',               'POST'),
('p', 'DRIVER', '/v1/ai/assistant/history/:id',        'GET'),
('p', 'DRIVER', '/v1/ai/assistant/history/:id',        'DELETE'),
('p', 'DRIVER', '/v1/ai/assistant/conversations/:id',  'GET')
ON CONFLICT DO NOTHING;
