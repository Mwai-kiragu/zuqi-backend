-- Allow DISTRIBUTOR_ADMIN and MERCHANT_ADMIN to update stock reorder thresholds
INSERT INTO casbin_rule (ptype, v0, v1, v2)
SELECT 'p', 'DISTRIBUTOR_ADMIN', '/v1/inventory/.*/thresholds', 'PATCH'
WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='DISTRIBUTOR_ADMIN' AND v1='/v1/inventory/.*/thresholds' AND v2='PATCH');

INSERT INTO casbin_rule (ptype, v0, v1, v2)
SELECT 'p', 'MERCHANT_ADMIN', '/v1/inventory/.*/thresholds', 'PATCH'
WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='MERCHANT_ADMIN' AND v1='/v1/inventory/.*/thresholds' AND v2='PATCH');
