-- Allow WAREHOUSE_MANAGER and ADMIN to update stock reorder thresholds
INSERT INTO casbin_rule (ptype, v0, v1, v2)
SELECT 'p', 'WAREHOUSE_MANAGER', '/v1/inventory/*/thresholds', 'PATCH'
WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='WAREHOUSE_MANAGER' AND v1='/v1/inventory/*/thresholds' AND v2='PATCH');

INSERT INTO casbin_rule (ptype, v0, v1, v2)
SELECT 'p', 'ADMIN', '/v1/inventory/*/thresholds', 'PATCH'
WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='ADMIN' AND v1='/v1/inventory/*/thresholds' AND v2='PATCH');

INSERT INTO casbin_rule (ptype, v0, v1, v2)
SELECT 'p', 'FINANCE', '/v1/inventory/*/thresholds', 'PATCH'
WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='FINANCE' AND v1='/v1/inventory/*/thresholds' AND v2='PATCH');
