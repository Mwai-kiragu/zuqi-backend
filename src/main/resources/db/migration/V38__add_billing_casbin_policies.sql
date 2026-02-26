-- Billing Casbin policies
-- SUPER_ADMIN and ADMIN bypass Casbin entirely via CasbinAuthorizationFilter
-- so no entries needed for them.

-- DISTRIBUTOR_ADMIN - read own subscription + package list
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
('p', 'DISTRIBUTOR_ADMIN', '/v1/billing/subscriptions/:id', 'GET'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/billing/packages', 'GET')
ON CONFLICT DO NOTHING;

-- FINANCE - same read-only access
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
('p', 'FINANCE', '/v1/billing/subscriptions/:id', 'GET'),
('p', 'FINANCE', '/v1/billing/packages', 'GET')
ON CONFLICT DO NOTHING;
