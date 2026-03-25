-- V159: Fix INITIATOR/VERIFIER/AUTHORIZER roles that were not inserted due to wrong column
-- name in V147 (used 'system_role' instead of 'is_system_role')

INSERT INTO roles (name, description, is_system_role) VALUES
  ('INITIATOR',  'Creates transactions (orders, transfers, bills)', true),
  ('VERIFIER',   'Reviews and submits transactions for approval',   true),
  ('AUTHORIZER', 'Final approver for transactions',                 true)
ON CONFLICT (name) DO NOTHING;
