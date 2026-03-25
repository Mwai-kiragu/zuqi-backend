-- Add approval workflow columns to promotions
ALTER TABLE promotions
  ADD COLUMN IF NOT EXISTS approval_status VARCHAR(30) DEFAULT 'APPROVED',
  ADD COLUMN IF NOT EXISTS created_by_id   UUID REFERENCES users(id);

-- Casbin: INITIATOR can create/view promotions; VERIFIER/AUTHORIZER can view
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
  ('p', 'INITIATOR',  '/v1/promotions',     'POST'),
  ('p', 'INITIATOR',  '/v1/promotions',     'GET'),
  ('p', 'INITIATOR',  '/v1/promotions/.*',  'GET'),
  ('p', 'VERIFIER',   '/v1/promotions',     'GET'),
  ('p', 'VERIFIER',   '/v1/promotions/.*',  'GET'),
  ('p', 'AUTHORIZER', '/v1/promotions',     'GET'),
  ('p', 'AUTHORIZER', '/v1/promotions/.*',  'GET')
ON CONFLICT DO NOTHING;
