-- Allow MERCHANT_ADMIN and DISTRIBUTOR_ADMIN to confirm and cancel sales returns.
-- V149 only granted PUT on /v1/sales-returns/.* but confirm and cancel are POST sub-paths.
-- The business owner (MERCHANT_ADMIN) must be able to action their own returns.
INSERT INTO casbin_rule (ptype, v0, v1, v2)
SELECT 'p', 'MERCHANT_ADMIN', '/v1/sales-returns/*/confirm', 'POST'
WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='MERCHANT_ADMIN' AND v1='/v1/sales-returns/*/confirm' AND v2='POST');

INSERT INTO casbin_rule (ptype, v0, v1, v2)
SELECT 'p', 'MERCHANT_ADMIN', '/v1/sales-returns/*/cancel', 'POST'
WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='MERCHANT_ADMIN' AND v1='/v1/sales-returns/*/cancel' AND v2='POST');

INSERT INTO casbin_rule (ptype, v0, v1, v2)
SELECT 'p', 'DISTRIBUTOR_ADMIN', '/v1/sales-returns/*/confirm', 'POST'
WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='DISTRIBUTOR_ADMIN' AND v1='/v1/sales-returns/*/confirm' AND v2='POST');

INSERT INTO casbin_rule (ptype, v0, v1, v2)
SELECT 'p', 'DISTRIBUTOR_ADMIN', '/v1/sales-returns/*/cancel', 'POST'
WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='DISTRIBUTOR_ADMIN' AND v1='/v1/sales-returns/*/cancel' AND v2='POST');
