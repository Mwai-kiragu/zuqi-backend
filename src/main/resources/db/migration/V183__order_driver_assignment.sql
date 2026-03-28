-- V183: Add driver assignment to orders

ALTER TABLE orders ADD COLUMN IF NOT EXISTS assigned_driver_id UUID REFERENCES users(id);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS assigned_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_orders_driver ON orders(assigned_driver_id);

-- Casbin policies for driver assignment endpoints
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
  ('p', 'SUPER_ADMIN',       '/v1/orders/.*/assign-driver', 'PATCH'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/orders/.*/assign-driver', 'PATCH'),
  ('p', 'MERCHANT_ADMIN',    '/v1/orders/.*/assign-driver', 'PATCH'),
  ('p', 'WAREHOUSE_MANAGER', '/v1/orders/.*/assign-driver', 'PATCH'),

  ('p', 'SUPER_ADMIN',       '/v1/orders/available-drivers', 'GET'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/orders/available-drivers', 'GET'),
  ('p', 'MERCHANT_ADMIN',    '/v1/orders/available-drivers', 'GET'),
  ('p', 'WAREHOUSE_MANAGER', '/v1/orders/available-drivers', 'GET'),
  ('p', 'DRIVER',            '/v1/orders/available-drivers', 'GET')

ON CONFLICT DO NOTHING;
