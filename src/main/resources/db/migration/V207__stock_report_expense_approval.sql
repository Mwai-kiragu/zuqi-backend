-- V207: Stock movement report access + expense approval Casbin rules

-- Allow all roles to read stock movements report
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
  ('p', 'DISTRIBUTOR_ADMIN',  '/v1/inventory/movements', 'GET'),
  ('p', 'MERCHANT_ADMIN',     '/v1/inventory/movements', 'GET'),
  ('p', 'WAREHOUSE_MANAGER',  '/v1/inventory/movements', 'GET'),
  ('p', 'FINANCE',            '/v1/inventory/movements', 'GET'),
  ('p', 'SALES_REP',          '/v1/inventory/movements', 'GET')
ON CONFLICT DO NOTHING;

-- Allow VERIFIER/AUTHORIZER to approve expenses via generic approval endpoint
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
  ('p', 'VERIFIER',   '/v1/expenses/.*/approve', 'PATCH'),
  ('p', 'AUTHORIZER', '/v1/expenses/.*/approve', 'PATCH'),
  ('p', 'VERIFIER',   '/v1/expenses/.*/reject',  'PATCH'),
  ('p', 'AUTHORIZER', '/v1/expenses/.*/reject',  'PATCH')
ON CONFLICT DO NOTHING;
