-- General Ledger Casbin policies for DISTRIBUTOR_ADMIN and FINANCE
-- SUPER_ADMIN and ADMIN bypass Casbin entirely via CasbinAuthorizationFilter

-- DISTRIBUTOR_ADMIN - full GL access
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
('p', 'DISTRIBUTOR_ADMIN', '/v1/gl/accounts', '.*'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/gl/accounts/:id', '.*'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/gl/accounts/:id/deactivate', 'POST'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/gl/accounts/seed', 'POST'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/gl/periods', '.*'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/gl/periods/:id', '.*'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/gl/periods/:id/close', 'POST'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/gl/periods/:id/lock', 'POST'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/gl/periods/:id/reopen', 'POST'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/gl/cost-centers', '.*'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/gl/cost-centers/:id', '.*'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/gl/cost-centers/:id/activate', 'POST'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/gl/journals', '.*'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/gl/journals/:id', '.*'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/gl/journals/:id/submit', 'POST'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/gl/journals/:id/approve', 'POST'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/gl/journals/:id/reject', 'POST'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/gl/journals/:id/reverse', 'POST'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/gl/budgets', '.*'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/gl/reports/trial-balance', 'GET'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/gl/reports/budget-variance', 'GET')
ON CONFLICT DO NOTHING;

-- FINANCE - read + create/update journals and budgets
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
('p', 'FINANCE', '/v1/gl/accounts', 'GET'),
('p', 'FINANCE', '/v1/gl/accounts/:id', 'GET'),
('p', 'FINANCE', '/v1/gl/periods', 'GET'),
('p', 'FINANCE', '/v1/gl/periods/:id', 'GET'),
('p', 'FINANCE', '/v1/gl/cost-centers', 'GET'),
('p', 'FINANCE', '/v1/gl/cost-centers/:id', 'GET'),
('p', 'FINANCE', '/v1/gl/journals', 'GET|POST'),
('p', 'FINANCE', '/v1/gl/journals/:id', 'GET|PUT'),
('p', 'FINANCE', '/v1/gl/journals/:id/submit', 'POST'),
('p', 'FINANCE', '/v1/gl/budgets', 'GET|POST'),
('p', 'FINANCE', '/v1/gl/reports/trial-balance', 'GET'),
('p', 'FINANCE', '/v1/gl/reports/budget-variance', 'GET')
ON CONFLICT DO NOTHING;
