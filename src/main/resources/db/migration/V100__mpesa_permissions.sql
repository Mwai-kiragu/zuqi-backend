-- Casbin policies for M-Pesa endpoints

-- SUPER_ADMIN: full access
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
  ('p', 'SUPER_ADMIN', '/v1/mpesa/*', 'GET'),
  ('p', 'SUPER_ADMIN', '/v1/mpesa/*', 'POST'),
  ('p', 'SUPER_ADMIN', '/v1/mpesa/*', 'DELETE'),
  ('p', 'SUPER_ADMIN', '/v1/mpesa/merchants/*/activate', 'POST')
ON CONFLICT DO NOTHING;

-- MERCHANT_ADMIN: manage their own configs, initiate STK
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
  ('p', 'MERCHANT_ADMIN', '/v1/mpesa/activate', 'POST'),
  ('p', 'MERCHANT_ADMIN', '/v1/mpesa/configs', 'GET'),
  ('p', 'MERCHANT_ADMIN', '/v1/mpesa/configs/*', 'DELETE'),
  ('p', 'MERCHANT_ADMIN', '/v1/mpesa/stk-push', 'POST'),
  ('p', 'MERCHANT_ADMIN', '/v1/mpesa/stk-push/*/status', 'GET')
ON CONFLICT DO NOTHING;

-- DISTRIBUTOR_ADMIN: view configs, initiate STK
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/mpesa/configs', 'GET'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/mpesa/stk-push', 'POST'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/mpesa/stk-push/*/status', 'GET')
ON CONFLICT DO NOTHING;

-- CASHIER: can initiate STK (for POS checkout)
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
  ('p', 'CASHIER', '/v1/mpesa/configs', 'GET'),
  ('p', 'CASHIER', '/v1/mpesa/stk-push', 'POST'),
  ('p', 'CASHIER', '/v1/mpesa/stk-push/*/status', 'GET')
ON CONFLICT DO NOTHING;

-- FINANCE: view + initiate
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
  ('p', 'FINANCE', '/v1/mpesa/configs', 'GET'),
  ('p', 'FINANCE', '/v1/mpesa/stk-push', 'POST'),
  ('p', 'FINANCE', '/v1/mpesa/stk-push/*/status', 'GET')
ON CONFLICT DO NOTHING;
