-- Allow all roles to fetch role details by name (used by AuthContext on login)
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'DISTRIBUTOR_ADMIN', '/v1/roles/name/:name', 'GET' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='DISTRIBUTOR_ADMIN' AND v1='/v1/roles/name/:name' AND v2='GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'SALES_REP', '/v1/roles/name/:name', 'GET' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='SALES_REP' AND v1='/v1/roles/name/:name' AND v2='GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'WAREHOUSE_MANAGER', '/v1/roles/name/:name', 'GET' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='WAREHOUSE_MANAGER' AND v1='/v1/roles/name/:name' AND v2='GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'MERCHANT', '/v1/roles/name/:name', 'GET' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='MERCHANT' AND v1='/v1/roles/name/:name' AND v2='GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'FINANCE', '/v1/roles/name/:name', 'GET' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='FINANCE' AND v1='/v1/roles/name/:name' AND v2='GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'DRIVER', '/v1/roles/name/:name', 'GET' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='DRIVER' AND v1='/v1/roles/name/:name' AND v2='GET');

-- KYC admin endpoints (SUPER_ADMIN wildcard already covers these, but explicit for clarity)
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'SUPER_ADMIN', '/v1/kyc/applications', 'GET' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='SUPER_ADMIN' AND v1='/v1/kyc/applications' AND v2='GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'SUPER_ADMIN', '/v1/kyc/applications/:id/approve', 'POST' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='SUPER_ADMIN' AND v1='/v1/kyc/applications/:id/approve' AND v2='POST');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'SUPER_ADMIN', '/v1/kyc/applications/:id/reject', 'POST' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='SUPER_ADMIN' AND v1='/v1/kyc/applications/:id/reject' AND v2='POST');
