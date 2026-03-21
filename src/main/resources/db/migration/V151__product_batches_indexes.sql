-- ===========================================================================================
-- V151__product_batches_indexes.sql
-- Ensures product_batches table exists (created in V135) and adds Casbin policies.
-- The CREATE TABLE IF NOT EXISTS is safe to re-run.
-- ===========================================================================================

-- Table already created in V135; guard ensures idempotency
CREATE TABLE IF NOT EXISTS product_batches (
  id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  distributor_id   UUID NOT NULL REFERENCES distributors(id),
  warehouse_id     UUID NOT NULL REFERENCES warehouses(id),
  product_id       UUID NOT NULL REFERENCES products(id),
  batch_number     VARCHAR(100) NOT NULL,
  manufacture_date DATE,
  expiry_date      DATE,
  initial_quantity DOUBLE PRECISION NOT NULL DEFAULT 0,
  current_quantity DOUBLE PRECISION NOT NULL DEFAULT 0,
  status           VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  created_at       TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at       TIMESTAMP,
  CONSTRAINT uq_product_batches_warehouse_product_batch UNIQUE (warehouse_id, product_id, batch_number)
);

CREATE INDEX IF NOT EXISTS idx_product_batch_warehouse_expiry    ON product_batches(warehouse_id, expiry_date);
CREATE INDEX IF NOT EXISTS idx_product_batch_product_expiry      ON product_batches(product_id, expiry_date);
CREATE INDEX IF NOT EXISTS idx_product_batch_distributor_status  ON product_batches(distributor_id, status);

-- Casbin policies for product batches
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
  ('p', 'DISTRIBUTOR_ADMIN',  '/v1/inventory/batches',    'GET'),
  ('p', 'DISTRIBUTOR_ADMIN',  '/v1/inventory/batches',    'POST'),
  ('p', 'DISTRIBUTOR_ADMIN',  '/v1/inventory/batches/.*', 'GET'),
  ('p', 'DISTRIBUTOR_ADMIN',  '/v1/inventory/batches/.*', 'PUT'),
  ('p', 'WAREHOUSE_MANAGER',  '/v1/inventory/batches',    'GET'),
  ('p', 'WAREHOUSE_MANAGER',  '/v1/inventory/batches',    'POST'),
  ('p', 'WAREHOUSE_MANAGER',  '/v1/inventory/batches/.*', 'GET'),
  ('p', 'WAREHOUSE_MANAGER',  '/v1/inventory/batches/.*', 'PUT'),
  ('p', 'MERCHANT_ADMIN',     '/v1/inventory/batches',    'GET'),
  ('p', 'MERCHANT_ADMIN',     '/v1/inventory/batches',    'POST'),
  ('p', 'MERCHANT_ADMIN',     '/v1/inventory/batches/.*', 'GET'),
  ('p', 'MERCHANT_ADMIN',     '/v1/inventory/batches/.*', 'PUT'),
  ('p', 'SUPER_ADMIN',        '/v1/inventory/batches',    'GET'),
  ('p', 'SUPER_ADMIN',        '/v1/inventory/batches/.*', 'GET')
ON CONFLICT DO NOTHING;
