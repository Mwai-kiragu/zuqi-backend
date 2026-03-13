-- MERCHANT_ADMIN: users/roles lookup and branches access
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
    ('p', 'MERCHANT_ADMIN', '/v1/users/roles', 'GET'),
    ('p', 'MERCHANT_ADMIN', '/v1/branches', 'GET'),
    ('p', 'MERCHANT_ADMIN', '/v1/branches/:id', 'GET'),
    ('p', 'MERCHANT_ADMIN', '/v1/branches', 'POST'),
    ('p', 'MERCHANT_ADMIN', '/v1/branches/:id', 'PUT'),
    ('p', 'MERCHANT_ADMIN', '/v1/branches/:id/activate', 'POST'),
    ('p', 'MERCHANT_ADMIN', '/v1/branches/:id/deactivate', 'POST'),
    ('p', 'MERCHANT_ADMIN', '/v1/branches/:id/users', 'GET'),
    ('p', 'MERCHANT_ADMIN', '/v1/branches/:id/users', 'POST'),
    ('p', 'MERCHANT_ADMIN', '/v1/branches/:id/users/:id', 'DELETE')
ON CONFLICT DO NOTHING;
