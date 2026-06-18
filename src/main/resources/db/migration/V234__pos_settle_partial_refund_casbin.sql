-- V234: Casbin policies for POS settle-balance and partial-refund endpoints.
-- Both endpoints were added after the original V56 POS policy set and were never granted.
-- Uses * glob (keyMatch), not .* regex — see V220 for the distinction.

INSERT INTO casbin_rule (ptype, v0, v1, v2)
SELECT 'p', role, '/v1/pos/sales/*/settle', 'POST'
FROM (VALUES
    ('DISTRIBUTOR_ADMIN'),
    ('WAREHOUSE_MANAGER'),
    ('SALES_REP'),
    ('MERCHANT_ADMIN')
) AS r(role)
WHERE NOT EXISTS (
    SELECT 1 FROM casbin_rule
    WHERE ptype = 'p' AND v0 = r.role AND v1 = '/v1/pos/sales/*/settle' AND v2 = 'POST'
);

INSERT INTO casbin_rule (ptype, v0, v1, v2)
SELECT 'p', role, '/v1/pos/sales/*/partial-refund', 'POST'
FROM (VALUES
    ('DISTRIBUTOR_ADMIN'),
    ('WAREHOUSE_MANAGER'),
    ('SALES_REP'),
    ('MERCHANT_ADMIN')
) AS r(role)
WHERE NOT EXISTS (
    SELECT 1 FROM casbin_rule
    WHERE ptype = 'p' AND v0 = r.role AND v1 = '/v1/pos/sales/*/partial-refund' AND v2 = 'POST'
);
