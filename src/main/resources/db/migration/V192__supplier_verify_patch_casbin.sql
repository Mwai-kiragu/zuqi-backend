-- Allow MERCHANT_ADMIN and DISTRIBUTOR_ADMIN to call PATCH /v1/suppliers/{id}/verify
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
  ('p', 'MERCHANT_ADMIN',    '/v1/suppliers/.*', 'PATCH'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/suppliers/.*', 'PATCH')
ON CONFLICT DO NOTHING;
