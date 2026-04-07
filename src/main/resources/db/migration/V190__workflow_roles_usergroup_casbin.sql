-- Allow workflow roles to fetch their own UserGroup and UserType.
-- These endpoints are called by the frontend at login time to load dynamic
-- module permissions from the UserType configuration. Without these rules,
-- the permission initialization fails with 403 and falls back to the static
-- role map, ignoring admin-configured UserType modules entirely.

INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
  -- /v1/user-groups/:id  (fetch own UserGroup to resolve UserType)
  ('p', 'INITIATOR',  '/v1/user-groups/:id', 'GET'),
  ('p', 'VERIFIER',   '/v1/user-groups/:id', 'GET'),
  ('p', 'AUTHORIZER', '/v1/user-groups/:id', 'GET'),

  -- /v1/user-types/:id   (fetch UserType permissions for sidebar init)
  ('p', 'INITIATOR',  '/v1/user-types/:id',  'GET'),
  ('p', 'VERIFIER',   '/v1/user-types/:id',  'GET'),
  ('p', 'AUTHORIZER', '/v1/user-types/:id',  'GET')

ON CONFLICT DO NOTHING;
