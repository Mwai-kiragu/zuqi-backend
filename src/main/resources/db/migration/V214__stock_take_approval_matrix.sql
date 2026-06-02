-- Allow FINANCE role to view and approve stock takes (for approval matrix)
INSERT INTO casbin_rule (ptype, v0, v1, v2)
SELECT 'p', 'FINANCE', '/v1/inventory/stock-takes',     'GET'
WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='FINANCE' AND v1='/v1/inventory/stock-takes' AND v2='GET');

INSERT INTO casbin_rule (ptype, v0, v1, v2)
SELECT 'p', 'FINANCE', '/v1/inventory/stock-takes/*',   'GET'
WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='FINANCE' AND v1='/v1/inventory/stock-takes/*' AND v2='GET');

INSERT INTO casbin_rule (ptype, v0, v1, v2)
SELECT 'p', 'FINANCE', '/v1/inventory/stock-takes/*/approve', 'POST'
WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='FINANCE' AND v1='/v1/inventory/stock-takes/*/approve' AND v2='POST');
