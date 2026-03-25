-- V158: MERCHANT_ADMIN full user management Casbin rules

INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
  ('p', 'MERCHANT_ADMIN', '/v1/users',                    'GET|POST'),
  ('p', 'MERCHANT_ADMIN', '/v1/users/role/:role',         'GET'),
  ('p', 'MERCHANT_ADMIN', '/v1/users/:id',                'GET|PUT|PATCH|DELETE'),
  ('p', 'MERCHANT_ADMIN', '/v1/users/:id/reset-password', 'POST'),
  ('p', 'MERCHANT_ADMIN', '/v1/users/:id/activate',       'POST')
ON CONFLICT DO NOTHING;
