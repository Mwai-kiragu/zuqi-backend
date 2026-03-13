-- Casbin policies for the cash-enabled toggle endpoint

-- MERCHANT_ADMIN: read + toggle cash
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
  ('p', 'MERCHANT_ADMIN', '/v1/mpesa/cash', 'GET'),
  ('p', 'MERCHANT_ADMIN', '/v1/mpesa/cash', 'PATCH')
ON CONFLICT DO NOTHING;

-- DISTRIBUTOR_ADMIN, CASHIER, FINANCE: read-only
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/mpesa/cash', 'GET'),
  ('p', 'CASHIER',           '/v1/mpesa/cash', 'GET'),
  ('p', 'FINANCE',           '/v1/mpesa/cash', 'GET')
ON CONFLICT DO NOTHING;
