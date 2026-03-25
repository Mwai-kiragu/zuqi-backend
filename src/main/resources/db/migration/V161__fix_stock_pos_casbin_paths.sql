-- V161: Fix Casbin paths for stock movement approval and POS shift reconciliation
-- V160 had incorrect path /v1/inventory/adjustments/.*/approve — correct path is /v1/inventory/movements/.*/approve

INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
  ('p', 'VERIFIER',          '/v1/inventory/movements/.*/approve', 'POST'),
  ('p', 'AUTHORIZER',        '/v1/inventory/movements/.*/approve', 'POST'),
  ('p', 'MERCHANT_ADMIN',    '/v1/inventory/movements/.*/approve', 'POST'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/inventory/movements/.*/approve', 'POST'),
  -- POS shift reconciliation (also correct if not already present)
  ('p', 'VERIFIER',          '/v1/pos/shifts/.*/reconcile',        'POST'),
  ('p', 'AUTHORIZER',        '/v1/pos/shifts/.*/reconcile',        'POST'),
  ('p', 'MERCHANT_ADMIN',    '/v1/pos/shifts/.*/reconcile',        'POST'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/pos/shifts/.*/reconcile',        'POST')
ON CONFLICT DO NOTHING;
