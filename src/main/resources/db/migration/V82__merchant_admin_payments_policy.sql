-- MERCHANT_ADMIN payment policies
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
    ('p', 'MERCHANT_ADMIN', '/v1/payments',                'GET'),
    ('p', 'MERCHANT_ADMIN', '/v1/payments',                'POST'),
    ('p', 'MERCHANT_ADMIN', '/v1/payments/:id',            'GET'),
    ('p', 'MERCHANT_ADMIN', '/v1/payments/number/:id',     'GET'),
    ('p', 'MERCHANT_ADMIN', '/v1/payments/methods',        'GET'),
    ('p', 'MERCHANT_ADMIN', '/v1/payments/:id/status',     'PATCH'),
    ('p', 'MERCHANT_ADMIN', '/v1/payments/unreconciled',   'GET')
ON CONFLICT DO NOTHING;
