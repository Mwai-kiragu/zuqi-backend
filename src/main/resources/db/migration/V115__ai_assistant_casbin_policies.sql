-- ===================================================
-- Casbin RBAC Policies — AI Assistant Chat Feature
-- Part of AI Chat Feature
-- Author: Zuqi Engineering
-- Date: 2026-03-13
-- ===================================================

-- DISTRIBUTOR_ADMIN: full access (chat + reports + history)
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
('p', 'DISTRIBUTOR_ADMIN', '/v1/ai/assistant/chat',                   'POST'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/ai/assistant/report',                 'POST'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/ai/assistant/history/:conversationId','GET'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/ai/assistant/conversations',          'GET')
ON CONFLICT DO NOTHING;

-- SALES_REP: chat and history only (no report generation)
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
('p', 'SALES_REP', '/v1/ai/assistant/chat',                   'POST'),
('p', 'SALES_REP', '/v1/ai/assistant/history/:conversationId','GET'),
('p', 'SALES_REP', '/v1/ai/assistant/conversations',          'GET')
ON CONFLICT DO NOTHING;

-- MERCHANT_ADMIN: chat and history only
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
('p', 'MERCHANT_ADMIN', '/v1/ai/assistant/chat',                   'POST'),
('p', 'MERCHANT_ADMIN', '/v1/ai/assistant/history/:conversationId','GET'),
('p', 'MERCHANT_ADMIN', '/v1/ai/assistant/conversations',          'GET')
ON CONFLICT DO NOTHING;

-- FINANCE: chat + reports (financial reports access)
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
('p', 'FINANCE', '/v1/ai/assistant/chat',                   'POST'),
('p', 'FINANCE', '/v1/ai/assistant/report',                 'POST'),
('p', 'FINANCE', '/v1/ai/assistant/history/:conversationId','GET'),
('p', 'FINANCE', '/v1/ai/assistant/conversations',          'GET')
ON CONFLICT DO NOTHING;
