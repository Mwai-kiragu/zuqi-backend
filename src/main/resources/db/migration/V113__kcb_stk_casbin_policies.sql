-- V113: Casbin policies for KCB STK push endpoints
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
  ('p', 'MERCHANT_ADMIN',    '/v1/kcb/stk-push',          'POST'),
  ('p', 'MERCHANT_ADMIN',    '/v1/kcb/stk-push/*/status', 'GET'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/kcb/stk-push',          'POST'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/kcb/stk-push/*/status', 'GET'),
  ('p', 'CASHIER',           '/v1/kcb/stk-push',          'POST'),
  ('p', 'CASHIER',           '/v1/kcb/stk-push/*/status', 'GET'),
  ('p', 'FINANCE',           '/v1/kcb/stk-push',          'POST'),
  ('p', 'FINANCE',           '/v1/kcb/stk-push/*/status', 'GET')
ON CONFLICT DO NOTHING;
