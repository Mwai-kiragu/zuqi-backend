-- Casbin policies for /v1/customers endpoint (formerly /v1/merchants for retailers)

INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
    -- DISTRIBUTOR_ADMIN: full CRUD
    ('p', 'DISTRIBUTOR_ADMIN', '/v1/customers',     'GET'),
    ('p', 'DISTRIBUTOR_ADMIN', '/v1/customers',     'POST'),
    ('p', 'DISTRIBUTOR_ADMIN', '/v1/customers/:id', 'GET'),
    ('p', 'DISTRIBUTOR_ADMIN', '/v1/customers/:id', 'PUT'),
    ('p', 'DISTRIBUTOR_ADMIN', '/v1/customers/:id', 'DELETE'),

    -- SALES_REP: create + read + update
    ('p', 'SALES_REP', '/v1/customers',     'GET'),
    ('p', 'SALES_REP', '/v1/customers',     'POST'),
    ('p', 'SALES_REP', '/v1/customers/:id', 'GET'),
    ('p', 'SALES_REP', '/v1/customers/:id', 'PUT'),

    -- MERCHANT_ADMIN: full CRUD (manages customers across all their distributors)
    ('p', 'MERCHANT_ADMIN', '/v1/customers',     'GET'),
    ('p', 'MERCHANT_ADMIN', '/v1/customers',     'POST'),
    ('p', 'MERCHANT_ADMIN', '/v1/customers/:id', 'GET'),
    ('p', 'MERCHANT_ADMIN', '/v1/customers/:id', 'PUT'),
    ('p', 'MERCHANT_ADMIN', '/v1/customers/:id', 'DELETE'),

    -- FINANCE: read-only
    ('p', 'FINANCE', '/v1/customers',     'GET'),
    ('p', 'FINANCE', '/v1/customers/:id', 'GET'),

    -- SUPER_ADMIN: full access (also add wildcard for any sub-paths)
    ('p', 'SUPER_ADMIN', '/v1/customers',     'GET'),
    ('p', 'SUPER_ADMIN', '/v1/customers',     'POST'),
    ('p', 'SUPER_ADMIN', '/v1/customers/:id', 'GET'),
    ('p', 'SUPER_ADMIN', '/v1/customers/:id', 'PUT'),
    ('p', 'SUPER_ADMIN', '/v1/customers/:id', 'DELETE')

ON CONFLICT DO NOTHING;
