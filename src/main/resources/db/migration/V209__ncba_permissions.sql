-- V209: Casbin policies for NCBA endpoints

-- SUPER_ADMIN: full access
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
  ('p', 'SUPER_ADMIN', '/v1/ncba/*', 'GET'),
  ('p', 'SUPER_ADMIN', '/v1/ncba/*', 'POST'),
  ('p', 'SUPER_ADMIN', '/v1/ncba/*', 'DELETE')
ON CONFLICT DO NOTHING;

-- MERCHANT_ADMIN: manage their own configs + initiate STK
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
  ('p', 'MERCHANT_ADMIN', '/v1/ncba/activate', 'POST'),
  ('p', 'MERCHANT_ADMIN', '/v1/ncba/configs', 'GET'),
  ('p', 'MERCHANT_ADMIN', '/v1/ncba/configs/*', 'DELETE'),
  ('p', 'MERCHANT_ADMIN', '/v1/ncba/stk-push', 'POST'),
  ('p', 'MERCHANT_ADMIN', '/v1/ncba/stk-push/*/status', 'GET')
ON CONFLICT DO NOTHING;

-- DISTRIBUTOR_ADMIN: view configs + initiate STK
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/ncba/configs', 'GET'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/ncba/stk-push', 'POST'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/ncba/stk-push/*/status', 'GET')
ON CONFLICT DO NOTHING;

-- CASHIER: view configs + initiate STK
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
  ('p', 'CASHIER', '/v1/ncba/configs', 'GET'),
  ('p', 'CASHIER', '/v1/ncba/stk-push', 'POST'),
  ('p', 'CASHIER', '/v1/ncba/stk-push/*/status', 'GET')
ON CONFLICT DO NOTHING;

-- FINANCE: view configs + initiate STK
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
  ('p', 'FINANCE', '/v1/ncba/configs', 'GET'),
  ('p', 'FINANCE', '/v1/ncba/stk-push', 'POST'),
  ('p', 'FINANCE', '/v1/ncba/stk-push/*/status', 'GET')
ON CONFLICT DO NOTHING;

-- SALES_REP: view configs (for payment at checkout)
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
  ('p', 'SALES_REP', '/v1/ncba/configs', 'GET'),
  ('p', 'SALES_REP', '/v1/ncba/stk-push', 'POST'),
  ('p', 'SALES_REP', '/v1/ncba/stk-push/*/status', 'GET')
ON CONFLICT DO NOTHING;

-- WAREHOUSE_MANAGER: read configs
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
  ('p', 'WAREHOUSE_MANAGER', '/v1/ncba/configs', 'GET')
ON CONFLICT DO NOTHING;
