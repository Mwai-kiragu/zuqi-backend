-- Add approval workflow columns to price_lists
ALTER TABLE price_lists
  ADD COLUMN IF NOT EXISTS approval_status VARCHAR(30) DEFAULT 'APPROVED',
  ADD COLUMN IF NOT EXISTS created_by_id   UUID REFERENCES users(id);

-- Casbin: allow INITIATOR to create price lists and view own requests
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
  ('p', 'INITIATOR',  '/v1/price-lists',       'POST'),
  ('p', 'INITIATOR',  '/v1/price-lists',        'GET'),
  ('p', 'INITIATOR',  '/v1/price-lists/.*',     'GET'),
  ('p', 'VERIFIER',   '/v1/price-lists',        'GET'),
  ('p', 'VERIFIER',   '/v1/price-lists/.*',     'GET'),
  ('p', 'AUTHORIZER', '/v1/price-lists',        'GET'),
  ('p', 'AUTHORIZER', '/v1/price-lists/.*',     'GET')
ON CONFLICT DO NOTHING;
