-- ===========================================================================================
-- V155__fix_audit_logs_casbin.sql
-- V147 added Casbin rules for /v1/activity-logs but the controller is mapped to
-- /v1/audit-logs. This migration adds the correct rules for all roles that should
-- have access to the audit log endpoint.
-- ===========================================================================================

INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
  ('p', 'SUPER_ADMIN',       '/v1/audit-logs',       'GET'),
  ('p', 'SUPER_ADMIN',       '/v1/audit-logs/.*',    'GET'),
  ('p', 'MERCHANT_ADMIN',    '/v1/audit-logs',       'GET'),
  ('p', 'MERCHANT_ADMIN',    '/v1/audit-logs/.*',    'GET'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/audit-logs',       'GET'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/audit-logs/.*',    'GET'),
  ('p', 'VERIFIER',          '/v1/audit-logs',       'GET'),
  ('p', 'VERIFIER',          '/v1/audit-logs/.*',    'GET'),
  ('p', 'AUTHORIZER',        '/v1/audit-logs',       'GET'),
  ('p', 'AUTHORIZER',        '/v1/audit-logs/.*',    'GET')
ON CONFLICT DO NOTHING;
