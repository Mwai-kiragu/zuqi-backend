-- V157: Casbin rules for MERCHANT_ADMIN and DISTRIBUTOR_ADMIN to access approval endpoints

INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
  ('p', 'MERCHANT_ADMIN',    '/v1/approvals',              'GET'),
  ('p', 'MERCHANT_ADMIN',    '/v1/approvals/.*',           'GET'),
  ('p', 'MERCHANT_ADMIN',    '/v1/approvals/.*/process',   'POST'),
  ('p', 'MERCHANT_ADMIN',    '/v1/approvals/.*/cancel',    'POST'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/approvals',              'GET'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/approvals/.*',           'GET'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/approvals/.*/process',   'POST'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/approvals/.*/cancel',    'POST')
ON CONFLICT DO NOTHING;
