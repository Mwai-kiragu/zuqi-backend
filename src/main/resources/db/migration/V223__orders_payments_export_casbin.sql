-- Casbin rules for orders and payments export endpoints (GET /v1/orders/export, GET /v1/payments/export)
-- and email export (POST /v1/export/orders/email, POST /v1/export/payments/email)

-- GET /v1/orders/export
INSERT INTO casbin_rule (ptype, v0, v1, v2)
SELECT 'p', r, '/v1/orders/export', 'GET'
FROM (VALUES
  ('SUPER_ADMIN'), ('MERCHANT_ADMIN'), ('DISTRIBUTOR_ADMIN'),
  ('FINANCE'), ('SALES_REP'), ('WAREHOUSE_MANAGER')
) AS t(r)
WHERE NOT EXISTS (
  SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0=t.r AND v1='/v1/orders/export' AND v2='GET'
);

-- GET /v1/payments/export
INSERT INTO casbin_rule (ptype, v0, v1, v2)
SELECT 'p', r, '/v1/payments/export', 'GET'
FROM (VALUES
  ('SUPER_ADMIN'), ('MERCHANT_ADMIN'), ('DISTRIBUTOR_ADMIN'),
  ('FINANCE'), ('SALES_REP')
) AS t(r)
WHERE NOT EXISTS (
  SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0=t.r AND v1='/v1/payments/export' AND v2='GET'
);

-- POST /v1/export/orders/email
INSERT INTO casbin_rule (ptype, v0, v1, v2)
SELECT 'p', r, '/v1/export/orders/email', 'POST'
FROM (VALUES
  ('SUPER_ADMIN'), ('MERCHANT_ADMIN'), ('DISTRIBUTOR_ADMIN'),
  ('FINANCE'), ('SALES_REP')
) AS t(r)
WHERE NOT EXISTS (
  SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0=t.r AND v1='/v1/export/orders/email' AND v2='POST'
);

-- POST /v1/export/payments/email
INSERT INTO casbin_rule (ptype, v0, v1, v2)
SELECT 'p', r, '/v1/export/payments/email', 'POST'
FROM (VALUES
  ('SUPER_ADMIN'), ('MERCHANT_ADMIN'), ('DISTRIBUTOR_ADMIN'),
  ('FINANCE')
) AS t(r)
WHERE NOT EXISTS (
  SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0=t.r AND v1='/v1/export/payments/email' AND v2='POST'
);
