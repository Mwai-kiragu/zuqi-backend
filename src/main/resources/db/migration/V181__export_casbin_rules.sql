-- Casbin policies for /v1/export/** endpoints
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
  ('p', 'SUPER_ADMIN',       '/v1/export/customers/email', 'POST'),
  ('p', 'SUPER_ADMIN',       '/v1/export/suppliers/email', 'POST'),
  ('p', 'SUPER_ADMIN',       '/v1/export/products/email',  'POST'),
  ('p', 'MERCHANT_ADMIN',    '/v1/export/customers/email', 'POST'),
  ('p', 'MERCHANT_ADMIN',    '/v1/export/suppliers/email', 'POST'),
  ('p', 'MERCHANT_ADMIN',    '/v1/export/products/email',  'POST'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/export/customers/email', 'POST'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/export/suppliers/email', 'POST'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/export/products/email',  'POST'),
  ('p', 'SALES_REP',         '/v1/export/customers/email', 'POST')
ON CONFLICT DO NOTHING;
