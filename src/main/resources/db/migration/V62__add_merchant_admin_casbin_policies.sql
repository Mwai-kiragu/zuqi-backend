-- MERCHANT_ADMIN Casbin policies
-- Grants access to profile, role lookup, billing, dashboard, and merchant/distributor management

INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
    -- Profile
    ('p', 'MERCHANT_ADMIN', '/v1/users/me', 'GET|PUT'),
    ('p', 'MERCHANT_ADMIN', '/v1/users/me/change-password', 'POST'),

    -- Role lookup (used by AuthContext on login)
    ('p', 'MERCHANT_ADMIN', '/v1/roles/name/:name', 'GET'),

    -- Billing (view own subscription and packages)
    ('p', 'MERCHANT_ADMIN', '/v1/billing/subscriptions/:id', 'GET'),
    ('p', 'MERCHANT_ADMIN', '/v1/billing/packages', 'GET'),

    -- Dashboard
    ('p', 'MERCHANT_ADMIN', '/v1/dashboard', 'GET'),
    ('p', 'MERCHANT_ADMIN', '/v1/dashboard/stats', 'GET'),
    ('p', 'MERCHANT_ADMIN', '/v1/dashboard/orders', 'GET'),
    ('p', 'MERCHANT_ADMIN', '/v1/dashboard/revenue', 'GET'),
    ('p', 'MERCHANT_ADMIN', '/v1/dashboard/top-products', 'GET'),

    -- Merchant brand (read own brand, update own brand)
    ('p', 'MERCHANT_ADMIN', '/v1/merchants', 'GET'),
    ('p', 'MERCHANT_ADMIN', '/v1/merchants/:id', 'GET|PUT'),

    -- Distributors under their merchant
    ('p', 'MERCHANT_ADMIN', '/v1/distributors', 'GET'),
    ('p', 'MERCHANT_ADMIN', '/v1/distributors/:id', 'GET'),

    -- Orders (read-only overview)
    ('p', 'MERCHANT_ADMIN', '/v1/orders', 'GET'),
    ('p', 'MERCHANT_ADMIN', '/v1/orders/:id', 'GET'),

    -- Products (read-only)
    ('p', 'MERCHANT_ADMIN', '/v1/products', 'GET'),
    ('p', 'MERCHANT_ADMIN', '/v1/products/:id', 'GET'),
    ('p', 'MERCHANT_ADMIN', '/v1/products/categories', 'GET'),
    ('p', 'MERCHANT_ADMIN', '/v1/products/categories/:id', 'GET'),

    -- Inventory (read-only)
    ('p', 'MERCHANT_ADMIN', '/v1/inventory', 'GET'),
    ('p', 'MERCHANT_ADMIN', '/v1/inventory/:id', 'GET'),

    -- Users under their distributors (read-only)
    ('p', 'MERCHANT_ADMIN', '/v1/users/distributor/:id', 'GET')

ON CONFLICT DO NOTHING;
