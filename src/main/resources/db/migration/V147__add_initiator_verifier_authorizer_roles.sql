-- ===========================================================================================
-- V147__add_initiator_verifier_authorizer_roles.sql
-- Adds INITIATOR, VERIFIER, AUTHORIZER roles + Casbin policies
-- Also fixes audit log access for DISTRIBUTOR_ADMIN, MERCHANT_ADMIN, AUTHORIZER
-- ===========================================================================================

INSERT INTO roles (name, description, system_role) VALUES
  ('INITIATOR', 'Creates transactions (orders, transfers, bills)', true),
  ('VERIFIER', 'Reviews and submits transactions for approval', true),
  ('AUTHORIZER', 'Final approver for transactions', true)
ON CONFLICT (name) DO NOTHING;

-- INITIATOR policies: create core entities + read access
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
  ('p', 'INITIATOR', '/v1/orders', 'POST'),
  ('p', 'INITIATOR', '/v1/supplier-bills', 'POST'),
  ('p', 'INITIATOR', '/v1/inventory/transfers', 'POST'),
  ('p', 'INITIATOR', '/v1/funds-transfers', 'POST'),
  ('p', 'INITIATOR', '/v1/orders', 'GET'),
  ('p', 'INITIATOR', '/v1/orders/.*', 'GET'),
  ('p', 'INITIATOR', '/v1/customers', 'GET'),
  ('p', 'INITIATOR', '/v1/customers/.*', 'GET'),
  ('p', 'INITIATOR', '/v1/products', 'GET'),
  ('p', 'INITIATOR', '/v1/products/.*', 'GET'),
  ('p', 'INITIATOR', '/v1/dashboard', 'GET'),
  ('p', 'INITIATOR', '/v1/supplier-bills', 'GET'),
  ('p', 'INITIATOR', '/v1/supplier-bills/.*', 'GET'),
  ('p', 'INITIATOR', '/v1/inventory/transfers', 'GET'),
  ('p', 'INITIATOR', '/v1/inventory/transfers/.*', 'GET'),
  ('p', 'INITIATOR', '/v1/funds-transfers', 'GET'),
  ('p', 'INITIATOR', '/v1/funds-transfers/.*', 'GET')
ON CONFLICT DO NOTHING;

-- VERIFIER policies: read + submit for approval
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
  ('p', 'VERIFIER', '/v1/orders', 'GET'),
  ('p', 'VERIFIER', '/v1/orders/.*', 'GET'),
  ('p', 'VERIFIER', '/v1/orders/.*', 'PUT'),
  ('p', 'VERIFIER', '/v1/orders/.*/submit', 'POST'),
  ('p', 'VERIFIER', '/v1/inventory/transfers', 'GET'),
  ('p', 'VERIFIER', '/v1/inventory/transfers/.*', 'GET'),
  ('p', 'VERIFIER', '/v1/inventory/transfers/.*/submit', 'POST'),
  ('p', 'VERIFIER', '/v1/supplier-bills', 'GET'),
  ('p', 'VERIFIER', '/v1/supplier-bills/.*', 'GET'),
  ('p', 'VERIFIER', '/v1/supplier-bills/.*/submit', 'POST'),
  ('p', 'VERIFIER', '/v1/funds-transfers', 'GET'),
  ('p', 'VERIFIER', '/v1/funds-transfers/.*', 'GET'),
  ('p', 'VERIFIER', '/v1/funds-transfers/.*/submit', 'POST'),
  ('p', 'VERIFIER', '/v1/dashboard', 'GET'),
  ('p', 'VERIFIER', '/v1/activity-logs', 'GET')
ON CONFLICT DO NOTHING;

-- AUTHORIZER policies: approve/reject + read
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
  ('p', 'AUTHORIZER', '/v1/orders', 'GET'),
  ('p', 'AUTHORIZER', '/v1/orders/.*', 'GET'),
  ('p', 'AUTHORIZER', '/v1/orders/.*/approve', 'POST'),
  ('p', 'AUTHORIZER', '/v1/orders/.*/reject', 'POST'),
  ('p', 'AUTHORIZER', '/v1/inventory/transfers', 'GET'),
  ('p', 'AUTHORIZER', '/v1/inventory/transfers/.*', 'GET'),
  ('p', 'AUTHORIZER', '/v1/inventory/transfers/.*/approve', 'POST'),
  ('p', 'AUTHORIZER', '/v1/inventory/transfers/.*/reject', 'POST'),
  ('p', 'AUTHORIZER', '/v1/supplier-bills', 'GET'),
  ('p', 'AUTHORIZER', '/v1/supplier-bills/.*', 'GET'),
  ('p', 'AUTHORIZER', '/v1/supplier-bills/.*/approve', 'POST'),
  ('p', 'AUTHORIZER', '/v1/supplier-bills/.*/reject', 'POST'),
  ('p', 'AUTHORIZER', '/v1/funds-transfers', 'GET'),
  ('p', 'AUTHORIZER', '/v1/funds-transfers/.*', 'GET'),
  ('p', 'AUTHORIZER', '/v1/funds-transfers/.*/approve', 'POST'),
  ('p', 'AUTHORIZER', '/v1/funds-transfers/.*/reject', 'POST'),
  ('p', 'AUTHORIZER', '/v1/dashboard', 'GET'),
  ('p', 'AUTHORIZER', '/v1/activity-logs', 'GET')
ON CONFLICT DO NOTHING;

-- Audit log access for DISTRIBUTOR_ADMIN and MERCHANT_ADMIN
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/activity-logs', 'GET'),
  ('p', 'MERCHANT_ADMIN', '/v1/activity-logs', 'GET')
ON CONFLICT DO NOTHING;
