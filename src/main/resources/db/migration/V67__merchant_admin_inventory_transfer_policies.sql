-- MERCHANT_ADMIN: stock transfers and stock takes access
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
    -- Stock Transfers
    ('p', 'MERCHANT_ADMIN', '/v1/inventory/transfers', 'GET'),
    ('p', 'MERCHANT_ADMIN', '/v1/inventory/transfers', 'POST'),
    ('p', 'MERCHANT_ADMIN', '/v1/inventory/transfers/:id', 'GET'),
    ('p', 'MERCHANT_ADMIN', '/v1/inventory/transfers/:id/approve', 'POST'),
    ('p', 'MERCHANT_ADMIN', '/v1/inventory/transfers/:id/cancel', 'POST'),
    ('p', 'MERCHANT_ADMIN', '/v1/inventory/transfers/:id/receive', 'POST'),
    -- Stock Takes
    ('p', 'MERCHANT_ADMIN', '/v1/inventory/stock-takes', 'GET'),
    ('p', 'MERCHANT_ADMIN', '/v1/inventory/stock-takes', 'POST'),
    ('p', 'MERCHANT_ADMIN', '/v1/inventory/stock-takes/:id', 'GET'),
    ('p', 'MERCHANT_ADMIN', '/v1/inventory/stock-takes/:id/items/:id', 'PUT'),
    ('p', 'MERCHANT_ADMIN', '/v1/inventory/stock-takes/:id/complete', 'POST'),
    ('p', 'MERCHANT_ADMIN', '/v1/inventory/stock-takes/:id/approve', 'POST'),
    ('p', 'MERCHANT_ADMIN', '/v1/inventory/stock-takes/:id/cancel', 'POST')
ON CONFLICT DO NOTHING;
