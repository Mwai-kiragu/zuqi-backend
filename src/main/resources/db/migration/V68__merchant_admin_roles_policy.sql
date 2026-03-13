-- MERCHANT_ADMIN: full roles list access (for user creation role dropdown)
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
    ('p', 'MERCHANT_ADMIN', '/v1/roles', 'GET'),
    ('p', 'MERCHANT_ADMIN', '/v1/roles/:id', 'GET')
ON CONFLICT DO NOTHING;
