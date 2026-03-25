-- Casbin rules for /v1/purchase-orders endpoints (missing from previous migrations)
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
  -- SUPER_ADMIN
  ('p', 'SUPER_ADMIN',       '/v1/purchase-orders',        'GET'),
  ('p', 'SUPER_ADMIN',       '/v1/purchase-orders/.*',     'GET'),
  ('p', 'SUPER_ADMIN',       '/v1/purchase-orders',        'POST'),
  ('p', 'SUPER_ADMIN',       '/v1/purchase-orders/.*',     'POST'),
  -- MERCHANT_ADMIN
  ('p', 'MERCHANT_ADMIN',    '/v1/purchase-orders',        'GET'),
  ('p', 'MERCHANT_ADMIN',    '/v1/purchase-orders/.*',     'GET'),
  ('p', 'MERCHANT_ADMIN',    '/v1/purchase-orders',        'POST'),
  ('p', 'MERCHANT_ADMIN',    '/v1/purchase-orders/.*',     'POST'),
  -- DISTRIBUTOR_ADMIN
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/purchase-orders',        'GET'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/purchase-orders/.*',     'GET'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/purchase-orders',        'POST'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/purchase-orders/.*',     'POST'),
  -- WAREHOUSE_MANAGER
  ('p', 'WAREHOUSE_MANAGER', '/v1/purchase-orders',        'GET'),
  ('p', 'WAREHOUSE_MANAGER', '/v1/purchase-orders/.*',     'GET'),
  ('p', 'WAREHOUSE_MANAGER', '/v1/purchase-orders',        'POST'),
  ('p', 'WAREHOUSE_MANAGER', '/v1/purchase-orders/.*',     'POST'),
  -- FINANCE (read only)
  ('p', 'FINANCE',           '/v1/purchase-orders',        'GET'),
  ('p', 'FINANCE',           '/v1/purchase-orders/.*',     'GET'),
  -- INITIATOR
  ('p', 'INITIATOR',         '/v1/purchase-orders',        'GET'),
  ('p', 'INITIATOR',         '/v1/purchase-orders/.*',     'GET'),
  ('p', 'INITIATOR',         '/v1/purchase-orders',        'POST'),
  ('p', 'INITIATOR',         '/v1/purchase-orders/.*',     'POST'),
  -- VERIFIER
  ('p', 'VERIFIER',          '/v1/purchase-orders',        'GET'),
  ('p', 'VERIFIER',          '/v1/purchase-orders/.*',     'GET'),
  ('p', 'VERIFIER',          '/v1/purchase-orders/.*',     'POST'),
  -- AUTHORIZER
  ('p', 'AUTHORIZER',        '/v1/purchase-orders',        'GET'),
  ('p', 'AUTHORIZER',        '/v1/purchase-orders/.*',     'GET'),
  ('p', 'AUTHORIZER',        '/v1/purchase-orders/.*',     'POST')
ON CONFLICT DO NOTHING;
