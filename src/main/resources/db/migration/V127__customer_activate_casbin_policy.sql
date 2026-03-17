-- Add missing Casbin policies for POST /v1/customers/:id/activate
-- and POST /v1/customers/:id/deactivate sub-actions

INSERT INTO casbin_rule (ptype, v0, v1, v2)
SELECT vals.ptype, vals.v0, vals.v1, vals.v2
FROM (VALUES
    ('p', 'SUPER_ADMIN',       '/v1/customers/:id/activate',   'POST'),
    ('p', 'SUPER_ADMIN',       '/v1/customers/:id/verify',     'PATCH'),
    ('p', 'MERCHANT_ADMIN',    '/v1/customers/:id/activate',   'POST'),
    ('p', 'MERCHANT_ADMIN',    '/v1/customers/:id/verify',     'PATCH'),
    ('p', 'DISTRIBUTOR_ADMIN', '/v1/customers/:id/activate',   'POST'),
    ('p', 'DISTRIBUTOR_ADMIN', '/v1/customers/:id/verify',     'PATCH'),
    ('p', 'SALES_REP',         '/v1/customers/:id/activate',   'POST')
) AS vals(ptype, v0, v1, v2)
WHERE NOT EXISTS (
    SELECT 1 FROM casbin_rule
    WHERE ptype = vals.ptype AND v0 = vals.v0 AND v1 = vals.v1 AND v2 = vals.v2
);
