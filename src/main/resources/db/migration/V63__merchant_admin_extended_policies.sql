-- MERCHANT_ADMIN extended policies: orders, products, inventory, users
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
    ('p', 'MERCHANT_ADMIN', '/v1/orders', 'GET'),
    ('p', 'MERCHANT_ADMIN', '/v1/orders/:id', 'GET'),
    ('p', 'MERCHANT_ADMIN', '/v1/products', 'GET'),
    ('p', 'MERCHANT_ADMIN', '/v1/products/:id', 'GET'),
    ('p', 'MERCHANT_ADMIN', '/v1/products/categories', 'GET'),
    ('p', 'MERCHANT_ADMIN', '/v1/products/categories/:id', 'GET'),
    ('p', 'MERCHANT_ADMIN', '/v1/inventory', 'GET'),
    ('p', 'MERCHANT_ADMIN', '/v1/inventory/:id', 'GET'),
    ('p', 'MERCHANT_ADMIN', '/v1/users/distributor/:id', 'GET')
ON CONFLICT DO NOTHING;
