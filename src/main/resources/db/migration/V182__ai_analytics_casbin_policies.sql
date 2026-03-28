-- V182: Add Casbin policies for /v1/ai/analytics/** endpoints
-- These were missing, causing 403 Access Denied for MERCHANT_ADMIN and DISTRIBUTOR_ADMIN

INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
  -- SUPER_ADMIN — full access
  ('p', 'SUPER_ADMIN',       '/v1/ai/analytics/.*', 'GET'),
  ('p', 'SUPER_ADMIN',       '/v1/ai/analytics/.*', 'POST'),

  -- MERCHANT_ADMIN — read all analytics + approve/reject actions
  ('p', 'MERCHANT_ADMIN',    '/v1/ai/analytics/.*', 'GET'),
  ('p', 'MERCHANT_ADMIN',    '/v1/ai/analytics/.*', 'POST'),

  -- DISTRIBUTOR_ADMIN — same as MERCHANT_ADMIN
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/ai/analytics/.*', 'GET'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/ai/analytics/.*', 'POST'),

  -- FINANCE — read-only analytics
  ('p', 'FINANCE',           '/v1/ai/analytics/.*', 'GET'),

  -- WAREHOUSE_MANAGER — inventory-related analytics
  ('p', 'WAREHOUSE_MANAGER', '/v1/ai/analytics/reorder/suggestions/:distributorId',        'GET'),
  ('p', 'WAREHOUSE_MANAGER', '/v1/ai/analytics/reorder/suggestions/:id/approve',           'POST'),
  ('p', 'WAREHOUSE_MANAGER', '/v1/ai/analytics/expiry/risks/:distributorId',               'GET'),
  ('p', 'WAREHOUSE_MANAGER', '/v1/ai/analytics/expiry/risks/:distributorId/:warehouseId',  'GET'),
  ('p', 'WAREHOUSE_MANAGER', '/v1/ai/analytics/expiry/run',                                'POST'),

  -- SALES_REP — customer and pricing analytics
  ('p', 'SALES_REP',         '/v1/ai/analytics/customers/segments/:distributorId',         'GET'),
  ('p', 'SALES_REP',         '/v1/ai/analytics/customers/churn/:distributorId',            'GET'),
  ('p', 'SALES_REP',         '/v1/ai/analytics/customers/churn/:distributorId/at-risk',    'GET'),
  ('p', 'SALES_REP',         '/v1/ai/analytics/customers/recommendations/:customerId',     'GET'),
  ('p', 'SALES_REP',         '/v1/ai/analytics/customers/product-recs/:distributorId',     'GET'),
  ('p', 'SALES_REP',         '/v1/ai/analytics/reps/:repId/visit-plan',                   'GET'),
  ('p', 'SALES_REP',         '/v1/ai/analytics/pricing/recommendations/:distributorId',    'GET')

ON CONFLICT DO NOTHING;
