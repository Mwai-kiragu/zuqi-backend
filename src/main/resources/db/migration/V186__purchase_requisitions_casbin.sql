-- V186: Casbin rules for /v1/purchase-requisitions endpoints (missing from previous migrations)

INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
  -- SUPER_ADMIN — full access
  ('p', 'SUPER_ADMIN',       '/v1/purchase-requisitions',                  'GET'),
  ('p', 'SUPER_ADMIN',       '/v1/purchase-requisitions/.*',               'GET'),
  ('p', 'SUPER_ADMIN',       '/v1/purchase-requisitions',                  'POST'),
  ('p', 'SUPER_ADMIN',       '/v1/purchase-requisitions/.*',               'POST'),

  -- MERCHANT_ADMIN — full access
  ('p', 'MERCHANT_ADMIN',    '/v1/purchase-requisitions',                  'GET'),
  ('p', 'MERCHANT_ADMIN',    '/v1/purchase-requisitions/.*',               'GET'),
  ('p', 'MERCHANT_ADMIN',    '/v1/purchase-requisitions',                  'POST'),
  ('p', 'MERCHANT_ADMIN',    '/v1/purchase-requisitions/.*',               'POST'),

  -- DISTRIBUTOR_ADMIN — full access
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/purchase-requisitions',                  'GET'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/purchase-requisitions/.*',               'GET'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/purchase-requisitions',                  'POST'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/purchase-requisitions/.*',               'POST'),

  -- WAREHOUSE_MANAGER — create and view
  ('p', 'WAREHOUSE_MANAGER', '/v1/purchase-requisitions',                  'GET'),
  ('p', 'WAREHOUSE_MANAGER', '/v1/purchase-requisitions/.*',               'GET'),
  ('p', 'WAREHOUSE_MANAGER', '/v1/purchase-requisitions',                  'POST'),
  ('p', 'WAREHOUSE_MANAGER', '/v1/purchase-requisitions/.*',               'POST'),

  -- FINANCE — view only
  ('p', 'FINANCE',           '/v1/purchase-requisitions',                  'GET'),
  ('p', 'FINANCE',           '/v1/purchase-requisitions/.*',               'GET'),

  -- INITIATOR — can create and view (submit/cancel their own PRs)
  ('p', 'INITIATOR',         '/v1/purchase-requisitions',                  'GET'),
  ('p', 'INITIATOR',         '/v1/purchase-requisitions/.*',               'GET'),
  ('p', 'INITIATOR',         '/v1/purchase-requisitions',                  'POST'),
  ('p', 'INITIATOR',         '/v1/purchase-requisitions/.*',               'POST'),

  -- VERIFIER — view and approve/reject
  ('p', 'VERIFIER',          '/v1/purchase-requisitions',                  'GET'),
  ('p', 'VERIFIER',          '/v1/purchase-requisitions/.*',               'GET'),
  ('p', 'VERIFIER',          '/v1/purchase-requisitions/.*',               'POST'),

  -- AUTHORIZER — view and approve/reject
  ('p', 'AUTHORIZER',        '/v1/purchase-requisitions',                  'GET'),
  ('p', 'AUTHORIZER',        '/v1/purchase-requisitions/.*',               'GET'),
  ('p', 'AUTHORIZER',        '/v1/purchase-requisitions/.*',               'POST')

ON CONFLICT DO NOTHING;
