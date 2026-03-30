-- Casbin policies for /v1/export/price-lists/email
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
  ('p', 'SUPER_ADMIN',       '/v1/export/price-lists/email', 'POST'),
  ('p', 'MERCHANT_ADMIN',    '/v1/export/price-lists/email', 'POST'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/export/price-lists/email', 'POST'),
  ('p', 'SALES_REP',         '/v1/export/price-lists/email', 'POST')
ON CONFLICT DO NOTHING;
