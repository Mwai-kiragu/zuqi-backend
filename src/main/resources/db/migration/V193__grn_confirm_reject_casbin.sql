-- Allow POST on /v1/grns/{id}/confirm and /v1/grns/{id}/reject
-- V166 only granted PUT on /v1/grns/.* — confirm and reject use POST
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
  ('p', 'SUPER_ADMIN',       '/v1/grns/.*', 'POST'),
  ('p', 'MERCHANT_ADMIN',    '/v1/grns/.*', 'POST'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/grns/.*', 'POST'),
  ('p', 'WAREHOUSE_MANAGER', '/v1/grns/.*', 'POST')
ON CONFLICT DO NOTHING;
