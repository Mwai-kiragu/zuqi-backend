-- Casbin policies for operational report endpoints
-- SUPER_ADMIN bypasses Casbin; these cover DISTRIBUTOR_ADMIN, MERCHANT_ADMIN, FINANCE
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
('p', 'DISTRIBUTOR_ADMIN', '/v1/reports/sales',      'GET'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/reports/inventory',  'GET'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/reports/payments',   'GET'),
('p', 'MERCHANT_ADMIN',    '/v1/reports/sales',      'GET'),
('p', 'MERCHANT_ADMIN',    '/v1/reports/inventory',  'GET'),
('p', 'MERCHANT_ADMIN',    '/v1/reports/payments',   'GET'),
('p', 'FINANCE',           '/v1/reports/sales',      'GET'),
('p', 'FINANCE',           '/v1/reports/inventory',  'GET'),
('p', 'FINANCE',           '/v1/reports/payments',   'GET'),
('p', 'SALES_REP',         '/v1/reports/sales',      'GET')
ON CONFLICT DO NOTHING;
