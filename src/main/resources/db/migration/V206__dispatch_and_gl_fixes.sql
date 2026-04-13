-- V206: Dispatch status update + GL accounts write access for FINANCE

-- Item 4: Allow WAREHOUSE_MANAGER, SALES_REP, DRIVER to update order status (dispatch flow)
-- The PATCH /v1/orders/{id}/status endpoint does not match /v1/orders/:id Casbin rule
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
  ('p', 'WAREHOUSE_MANAGER', '/v1/orders/.*/status',       'PATCH'),
  ('p', 'SALES_REP',         '/v1/orders/.*/status',       'PATCH'),
  ('p', 'DRIVER',            '/v1/orders/.*/status',       'PATCH'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/orders/.*/status',       'PATCH'),
  ('p', 'MERCHANT_ADMIN',    '/v1/orders/.*/status',       'PATCH')
ON CONFLICT DO NOTHING;

-- Item 12: Allow FINANCE to create and update GL accounts
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
  ('p', 'FINANCE', '/v1/gl/accounts',     'POST'),
  ('p', 'FINANCE', '/v1/gl/accounts/:id', 'PUT')
ON CONFLICT DO NOTHING;
