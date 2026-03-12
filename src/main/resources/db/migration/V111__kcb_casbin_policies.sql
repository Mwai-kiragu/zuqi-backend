-- V111: Casbin policies for KCB endpoints

-- SUPER_ADMIN: full access
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
  ('p', 'SUPER_ADMIN', '/v1/kcb/*', 'GET'),
  ('p', 'SUPER_ADMIN', '/v1/kcb/*', 'POST'),
  ('p', 'SUPER_ADMIN', '/v1/kcb/*', 'DELETE')
ON CONFLICT DO NOTHING;

-- MERCHANT_ADMIN: manage their own configs
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
  ('p', 'MERCHANT_ADMIN', '/v1/kcb/activate', 'POST'),
  ('p', 'MERCHANT_ADMIN', '/v1/kcb/configs', 'GET'),
  ('p', 'MERCHANT_ADMIN', '/v1/kcb/configs/*', 'DELETE')
ON CONFLICT DO NOTHING;

-- DISTRIBUTOR_ADMIN: view configs only
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/kcb/configs', 'GET')
ON CONFLICT DO NOTHING;

-- CASHIER: view configs (to know if KCB is available at checkout)
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
  ('p', 'CASHIER', '/v1/kcb/configs', 'GET')
ON CONFLICT DO NOTHING;

-- FINANCE: view configs
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
  ('p', 'FINANCE', '/v1/kcb/configs', 'GET')
ON CONFLICT DO NOTHING;
