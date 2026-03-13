-- Grant MERCHANT_ADMIN full CRUD access to products and product categories

INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
    -- Products
    ('p', 'MERCHANT_ADMIN', '/v1/products', 'POST'),
    ('p', 'MERCHANT_ADMIN', '/v1/products/:id', 'PUT'),
    ('p', 'MERCHANT_ADMIN', '/v1/products/:id', 'DELETE'),
    ('p', 'MERCHANT_ADMIN', '/v1/products/:id/activate', 'POST'),
    -- Product Categories
    ('p', 'MERCHANT_ADMIN', '/v1/products/categories', 'POST'),
    ('p', 'MERCHANT_ADMIN', '/v1/products/categories/:id', 'PUT'),
    ('p', 'MERCHANT_ADMIN', '/v1/products/categories/:id', 'DELETE'),
    ('p', 'MERCHANT_ADMIN', '/v1/products/categories/:id/activate', 'POST')
ON CONFLICT DO NOTHING;
