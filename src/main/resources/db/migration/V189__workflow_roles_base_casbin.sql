-- Base Casbin policies for workflow roles: INITIATOR, VERIFIER, AUTHORIZER
-- These roles were missing essential self-service endpoints (profile, change-password, billing, role lookup)

INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
  -- /v1/users/me  (view & update own profile)
  ('p', 'INITIATOR',  '/v1/users/me', 'GET'),
  ('p', 'INITIATOR',  '/v1/users/me', 'PUT'),
  ('p', 'VERIFIER',   '/v1/users/me', 'GET'),
  ('p', 'VERIFIER',   '/v1/users/me', 'PUT'),
  ('p', 'AUTHORIZER', '/v1/users/me', 'GET'),
  ('p', 'AUTHORIZER', '/v1/users/me', 'PUT'),

  -- /v1/users/me/change-password  (mandatory on first login)
  ('p', 'INITIATOR',  '/v1/users/me/change-password', 'POST'),
  ('p', 'VERIFIER',   '/v1/users/me/change-password', 'POST'),
  ('p', 'AUTHORIZER', '/v1/users/me/change-password', 'POST'),

  -- /v1/users/me/settings/*  (notification & security settings)
  ('p', 'INITIATOR',  '/v1/users/me/settings/notifications', 'GET'),
  ('p', 'INITIATOR',  '/v1/users/me/settings/notifications', 'PUT'),
  ('p', 'INITIATOR',  '/v1/users/me/settings/security',      'GET'),
  ('p', 'VERIFIER',   '/v1/users/me/settings/notifications', 'GET'),
  ('p', 'VERIFIER',   '/v1/users/me/settings/notifications', 'PUT'),
  ('p', 'VERIFIER',   '/v1/users/me/settings/security',      'GET'),
  ('p', 'AUTHORIZER', '/v1/users/me/settings/notifications', 'GET'),
  ('p', 'AUTHORIZER', '/v1/users/me/settings/notifications', 'PUT'),
  ('p', 'AUTHORIZER', '/v1/users/me/settings/security',      'GET'),

  -- /v1/roles/name/:name  (role display lookup on login)
  ('p', 'INITIATOR',  '/v1/roles/name/:name', 'GET'),
  ('p', 'VERIFIER',   '/v1/roles/name/:name', 'GET'),
  ('p', 'AUTHORIZER', '/v1/roles/name/:name', 'GET'),

  -- /v1/billing/subscriptions/:id  (subscription check on login)
  ('p', 'INITIATOR',  '/v1/billing/subscriptions/:id', 'GET'),
  ('p', 'VERIFIER',   '/v1/billing/subscriptions/:id', 'GET'),
  ('p', 'AUTHORIZER', '/v1/billing/subscriptions/:id', 'GET'),

  -- /v1/approvals  (core workflow — view and act on approvals)
  ('p', 'INITIATOR',  '/v1/approvals',     'GET'),
  ('p', 'INITIATOR',  '/v1/approvals',     'POST'),
  ('p', 'INITIATOR',  '/v1/approvals/.*',  'GET'),
  ('p', 'INITIATOR',  '/v1/approvals/.*',  'POST'),
  ('p', 'VERIFIER',   '/v1/approvals',     'GET'),
  ('p', 'VERIFIER',   '/v1/approvals',     'POST'),
  ('p', 'VERIFIER',   '/v1/approvals/.*',  'GET'),
  ('p', 'VERIFIER',   '/v1/approvals/.*',  'POST'),
  ('p', 'AUTHORIZER', '/v1/approvals',     'GET'),
  ('p', 'AUTHORIZER', '/v1/approvals',     'POST'),
  ('p', 'AUTHORIZER', '/v1/approvals/.*',  'GET'),
  ('p', 'AUTHORIZER', '/v1/approvals/.*',  'POST')

ON CONFLICT DO NOTHING;
