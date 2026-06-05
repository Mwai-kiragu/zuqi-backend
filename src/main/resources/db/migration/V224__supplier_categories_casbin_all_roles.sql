-- V131 only granted GET /v1/suppliers/categories to MERCHANT_ADMIN.
-- All other roles that can view supplier pages get a 403 when the form calls
-- this endpoint, causing the Category dropdown to show empty.
-- Fix: grant GET to all roles that can access suppliers; grant POST/PUT/DELETE
-- to SUPER_ADMIN and DISTRIBUTOR_ADMIN who manage categories.

INSERT INTO casbin_rule (ptype, v0, v1, v2)
SELECT 'p', r, '/v1/suppliers/categories', 'GET'
FROM (VALUES
  ('SUPER_ADMIN'), ('DISTRIBUTOR_ADMIN'), ('FINANCE'),
  ('SALES_REP'), ('WAREHOUSE_MANAGER'), ('INITIATOR'), ('VERIFIER'), ('AUTHORIZER')
) AS t(r)
WHERE NOT EXISTS (
  SELECT 1 FROM casbin_rule
  WHERE ptype='p' AND v0=t.r AND v1='/v1/suppliers/categories' AND v2='GET'
);

INSERT INTO casbin_rule (ptype, v0, v1, v2)
SELECT 'p', r, '/v1/suppliers/categories', 'POST'
FROM (VALUES ('SUPER_ADMIN'), ('DISTRIBUTOR_ADMIN')) AS t(r)
WHERE NOT EXISTS (
  SELECT 1 FROM casbin_rule
  WHERE ptype='p' AND v0=t.r AND v1='/v1/suppliers/categories' AND v2='POST'
);

INSERT INTO casbin_rule (ptype, v0, v1, v2)
SELECT 'p', r, '/v1/suppliers/categories/.*', 'PUT'
FROM (VALUES ('SUPER_ADMIN'), ('DISTRIBUTOR_ADMIN')) AS t(r)
WHERE NOT EXISTS (
  SELECT 1 FROM casbin_rule
  WHERE ptype='p' AND v0=t.r AND v1='/v1/suppliers/categories/.*' AND v2='PUT'
);

INSERT INTO casbin_rule (ptype, v0, v1, v2)
SELECT 'p', r, '/v1/suppliers/categories/.*', 'DELETE'
FROM (VALUES ('SUPER_ADMIN'), ('DISTRIBUTOR_ADMIN')) AS t(r)
WHERE NOT EXISTS (
  SELECT 1 FROM casbin_rule
  WHERE ptype='p' AND v0=t.r AND v1='/v1/suppliers/categories/.*' AND v2='DELETE'
);
