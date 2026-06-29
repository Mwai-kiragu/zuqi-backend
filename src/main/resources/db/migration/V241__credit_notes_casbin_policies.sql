-- Casbin access policies for the Credit Notes module (/v1/credit-notes)
INSERT INTO casbin_rule (ptype, v0, v1, v2)
SELECT * FROM (VALUES
  -- DISTRIBUTOR_ADMIN: full CRUD + sub-actions (/apply, /refund)
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/credit-notes',     'GET'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/credit-notes',     'POST'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/credit-notes/.*',  'GET'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/credit-notes/.*',  'PUT'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/credit-notes/.*',  'POST'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/credit-notes/.*',  'DELETE'),

  -- MERCHANT_ADMIN: read + apply
  ('p', 'MERCHANT_ADMIN',    '/v1/credit-notes',     'GET'),
  ('p', 'MERCHANT_ADMIN',    '/v1/credit-notes/.*',  'GET'),
  ('p', 'MERCHANT_ADMIN',    '/v1/credit-notes/.*',  'POST'),

  -- FINANCE: full read + apply/refund
  ('p', 'FINANCE',           '/v1/credit-notes',     'GET'),
  ('p', 'FINANCE',           '/v1/credit-notes/.*',  'GET'),
  ('p', 'FINANCE',           '/v1/credit-notes/.*',  'POST'),

  -- SUPER_ADMIN: bypass handled in filter but add explicit policies for consistency
  ('p', 'SUPER_ADMIN',       '/v1/credit-notes',     'GET'),
  ('p', 'SUPER_ADMIN',       '/v1/credit-notes/.*',  'GET')
) AS vals (ptype, v0, v1, v2)
WHERE NOT EXISTS (
    SELECT 1 FROM casbin_rule cr
    WHERE cr.ptype = vals.ptype
      AND cr.v0    = vals.v0
      AND cr.v1    = vals.v1
      AND cr.v2    = vals.v2
);
