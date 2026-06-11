-- V233: Grant POST on /v1/purchase-returns/.* so confirm and cancel actions
-- (/v1/purchase-returns/{id}/confirm and /v1/purchase-returns/{id}/cancel) are
-- permitted. The original V149 migration only granted PUT on this path.

INSERT INTO casbin_rule (ptype, v0, v1, v2)
SELECT 'p', 'DISTRIBUTOR_ADMIN', '/v1/purchase-returns/.*', 'POST'
WHERE NOT EXISTS (
    SELECT 1 FROM casbin_rule
    WHERE ptype='p' AND v0='DISTRIBUTOR_ADMIN'
      AND v1='/v1/purchase-returns/.*' AND v2='POST'
);

INSERT INTO casbin_rule (ptype, v0, v1, v2)
SELECT 'p', 'MERCHANT_ADMIN', '/v1/purchase-returns/.*', 'POST'
WHERE NOT EXISTS (
    SELECT 1 FROM casbin_rule
    WHERE ptype='p' AND v0='MERCHANT_ADMIN'
      AND v1='/v1/purchase-returns/.*' AND v2='POST'
);

INSERT INTO casbin_rule (ptype, v0, v1, v2)
SELECT 'p', 'FINANCE', '/v1/purchase-returns/.*', 'POST'
WHERE NOT EXISTS (
    SELECT 1 FROM casbin_rule
    WHERE ptype='p' AND v0='FINANCE'
      AND v1='/v1/purchase-returns/.*' AND v2='POST'
);
