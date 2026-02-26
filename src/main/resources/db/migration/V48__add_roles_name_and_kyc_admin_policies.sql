-- Allow all roles to fetch role details by name (used by AuthContext on login)
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'DISTRIBUTOR_ADMIN', '/v1/roles/name/:name', 'GET') ON CONFLICT DO NOTHING;
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'SALES_REP', '/v1/roles/name/:name', 'GET') ON CONFLICT DO NOTHING;
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'WAREHOUSE_MANAGER', '/v1/roles/name/:name', 'GET') ON CONFLICT DO NOTHING;
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'MERCHANT', '/v1/roles/name/:name', 'GET') ON CONFLICT DO NOTHING;
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'FINANCE', '/v1/roles/name/:name', 'GET') ON CONFLICT DO NOTHING;
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'DRIVER', '/v1/roles/name/:name', 'GET') ON CONFLICT DO NOTHING;

-- KYC admin endpoints (SUPER_ADMIN wildcard already covers these, but explicit for clarity)
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'SUPER_ADMIN', '/v1/kyc/applications', 'GET') ON CONFLICT DO NOTHING;
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'SUPER_ADMIN', '/v1/kyc/applications/:id/approve', 'POST') ON CONFLICT DO NOTHING;
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'SUPER_ADMIN', '/v1/kyc/applications/:id/reject', 'POST') ON CONFLICT DO NOTHING;
