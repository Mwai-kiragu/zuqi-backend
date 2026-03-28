-- Casbin policies for additional /v1/export/** endpoints
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
  -- inventory
  ('p', 'SUPER_ADMIN',       '/v1/export/inventory/email',         'POST'),
  ('p', 'MERCHANT_ADMIN',    '/v1/export/inventory/email',         'POST'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/export/inventory/email',         'POST'),
  -- invoices
  ('p', 'SUPER_ADMIN',       '/v1/export/invoices/email',          'POST'),
  ('p', 'MERCHANT_ADMIN',    '/v1/export/invoices/email',          'POST'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/export/invoices/email',          'POST'),
  ('p', 'FINANCE',           '/v1/export/invoices/email',          'POST'),
  -- warehouses
  ('p', 'SUPER_ADMIN',       '/v1/export/warehouses/email',        'POST'),
  ('p', 'MERCHANT_ADMIN',    '/v1/export/warehouses/email',        'POST'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/export/warehouses/email',        'POST'),
  -- branches
  ('p', 'SUPER_ADMIN',       '/v1/export/branches/email',          'POST'),
  ('p', 'MERCHANT_ADMIN',    '/v1/export/branches/email',          'POST'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/export/branches/email',          'POST'),
  -- categories
  ('p', 'SUPER_ADMIN',       '/v1/export/categories/email',        'POST'),
  ('p', 'MERCHANT_ADMIN',    '/v1/export/categories/email',        'POST'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/export/categories/email',        'POST'),
  ('p', 'SALES_REP',         '/v1/export/categories/email',        'POST'),
  -- pos-sales
  ('p', 'SUPER_ADMIN',       '/v1/export/pos-sales/email',         'POST'),
  ('p', 'MERCHANT_ADMIN',    '/v1/export/pos-sales/email',         'POST'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/export/pos-sales/email',         'POST'),
  ('p', 'FINANCE',           '/v1/export/pos-sales/email',         'POST'),
  -- financial-report
  ('p', 'SUPER_ADMIN',       '/v1/export/financial-report/email',  'POST'),
  ('p', 'MERCHANT_ADMIN',    '/v1/export/financial-report/email',  'POST'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/export/financial-report/email',  'POST'),
  ('p', 'FINANCE',           '/v1/export/financial-report/email',  'POST')
ON CONFLICT DO NOTHING;
