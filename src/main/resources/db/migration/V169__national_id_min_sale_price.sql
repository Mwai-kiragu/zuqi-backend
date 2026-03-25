-- Customer: national_id (mandatory going forward)
ALTER TABLE customers
  ADD COLUMN IF NOT EXISTS national_id VARCHAR(20);

-- Product: min_sale_price floor price
ALTER TABLE products
  ADD COLUMN IF NOT EXISTS min_sale_price NUMERIC(15, 2);

-- Casbin: bulk import endpoints
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
  ('p', 'SUPER_ADMIN',       '/v1/import/.*', 'POST'),
  ('p', 'MERCHANT_ADMIN',    '/v1/import/.*', 'POST'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/import/.*', 'POST')
ON CONFLICT DO NOTHING;
