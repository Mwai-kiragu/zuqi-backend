-- Rename MERCHANT role to CUSTOMER
UPDATE roles SET name = 'CUSTOMER' WHERE name = 'MERCHANT';

-- Insert MERCHANT_ADMIN role if not exists
INSERT INTO roles (name) VALUES ('MERCHANT_ADMIN') ON CONFLICT (name) DO NOTHING;

-- Update casbin policies: rename MERCHANT to CUSTOMER
UPDATE casbin_rule SET v0 = 'ROLE_CUSTOMER' WHERE v0 = 'ROLE_MERCHANT';

-- Add MERCHANT_ADMIN casbin policies for merchant brand management
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
    ('p', 'ROLE_MERCHANT_ADMIN', '/v1/merchants', 'GET'),
    ('p', 'ROLE_MERCHANT_ADMIN', '/v1/merchants/*', 'GET'),
    ('p', 'ROLE_MERCHANT_ADMIN', '/v1/merchants/*', 'PUT'),
    ('p', 'ROLE_MERCHANT_ADMIN', '/v1/distributors', 'GET'),
    ('p', 'ROLE_MERCHANT_ADMIN', '/v1/distributors/*', 'GET'),
    ('p', 'ROLE_SUPER_ADMIN', '/v1/merchants', 'GET'),
    ('p', 'ROLE_SUPER_ADMIN', '/v1/merchants/*', 'GET'),
    ('p', 'ROLE_SUPER_ADMIN', '/v1/merchants', 'POST'),
    ('p', 'ROLE_SUPER_ADMIN', '/v1/merchants/*', 'PUT'),
    ('p', 'ROLE_SUPER_ADMIN', '/v1/merchants/*', 'DELETE')
ON CONFLICT DO NOTHING;
