-- V156: Add approval_status + created_by_id to master data tables
-- Default to APPROVED so all existing records are unaffected

ALTER TABLE customers
  ADD COLUMN IF NOT EXISTS approval_status VARCHAR(30) DEFAULT 'APPROVED',
  ADD COLUMN IF NOT EXISTS created_by_id UUID REFERENCES users(id);

ALTER TABLE suppliers
  ADD COLUMN IF NOT EXISTS approval_status VARCHAR(30) DEFAULT 'APPROVED',
  ADD COLUMN IF NOT EXISTS created_by_id UUID REFERENCES users(id);

ALTER TABLE products
  ADD COLUMN IF NOT EXISTS approval_status VARCHAR(30) DEFAULT 'APPROVED',
  ADD COLUMN IF NOT EXISTS created_by_id UUID REFERENCES users(id);

-- Casbin: VERIFIER and AUTHORIZER can access approval endpoints
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
  ('p', 'VERIFIER',   '/v1/approvals',             'GET'),
  ('p', 'VERIFIER',   '/v1/approvals/.*',           'GET'),
  ('p', 'VERIFIER',   '/v1/approvals/.*/process',   'POST'),
  ('p', 'AUTHORIZER', '/v1/approvals',             'GET'),
  ('p', 'AUTHORIZER', '/v1/approvals/.*',           'GET'),
  ('p', 'AUTHORIZER', '/v1/approvals/.*/process',   'POST'),
  ('p', 'INITIATOR',  '/v1/approvals',             'GET'),
  ('p', 'INITIATOR',  '/v1/approvals/my-requests', 'GET'),
  ('p', 'INITIATOR',  '/v1/approvals/.*/cancel',   'POST')
ON CONFLICT DO NOTHING;
