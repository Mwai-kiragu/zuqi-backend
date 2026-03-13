-- Allow MERCHANT_ADMIN to create and manage distributors under their merchant
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
    ('p', 'MERCHANT_ADMIN', '/v1/distributors', 'POST'),
    ('p', 'MERCHANT_ADMIN', '/v1/distributors/:id', 'PUT'),
    ('p', 'MERCHANT_ADMIN', '/v1/distributors/:id/activate', 'POST'),
    ('p', 'MERCHANT_ADMIN', '/v1/distributors/:id/deactivate', 'POST')
ON CONFLICT DO NOTHING;
