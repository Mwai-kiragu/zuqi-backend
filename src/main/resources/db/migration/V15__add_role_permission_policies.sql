-- Add explicit Casbin policies for role and permission management
-- SUPER_ADMIN and ADMIN should have full access to manage roles and permissions

-- First, remove any existing wildcard policies for SUPER_ADMIN to avoid conflicts
-- Then add explicit role management policies

-- Role management policies for SUPER_ADMIN
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'SUPER_ADMIN', '/v1/roles', '.*')
ON CONFLICT DO NOTHING;
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'SUPER_ADMIN', '/v1/roles/:id', '.*')
ON CONFLICT DO NOTHING;
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'SUPER_ADMIN', '/v1/roles/:id/permissions', '.*')
ON CONFLICT DO NOTHING;
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'SUPER_ADMIN', '/v1/roles/system', '.*')
ON CONFLICT DO NOTHING;
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'SUPER_ADMIN', '/v1/roles/custom', '.*')
ON CONFLICT DO NOTHING;
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'SUPER_ADMIN', '/v1/roles/name/:name', '.*')
ON CONFLICT DO NOTHING;

-- Permission management policies for SUPER_ADMIN
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'SUPER_ADMIN', '/v1/permissions', '.*')
ON CONFLICT DO NOTHING;
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'SUPER_ADMIN', '/v1/permissions/:id', '.*')
ON CONFLICT DO NOTHING;
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'SUPER_ADMIN', '/v1/permissions/modules', '.*')
ON CONFLICT DO NOTHING;
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'SUPER_ADMIN', '/v1/permissions/module/:module', '.*')
ON CONFLICT DO NOTHING;
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'SUPER_ADMIN', '/v1/permissions/name/:name', '.*')
ON CONFLICT DO NOTHING;

-- Role management policies for ADMIN (inherits to SUPER_ADMIN via role hierarchy)
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'ADMIN', '/v1/roles', '.*')
ON CONFLICT DO NOTHING;
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'ADMIN', '/v1/roles/:id', '.*')
ON CONFLICT DO NOTHING;
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'ADMIN', '/v1/roles/:id/permissions', '.*')
ON CONFLICT DO NOTHING;
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'ADMIN', '/v1/roles/system', '.*')
ON CONFLICT DO NOTHING;
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'ADMIN', '/v1/roles/custom', '.*')
ON CONFLICT DO NOTHING;
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'ADMIN', '/v1/roles/name/:name', '.*')
ON CONFLICT DO NOTHING;

-- Permission management policies for ADMIN
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'ADMIN', '/v1/permissions', '.*')
ON CONFLICT DO NOTHING;
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'ADMIN', '/v1/permissions/:id', '.*')
ON CONFLICT DO NOTHING;
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'ADMIN', '/v1/permissions/modules', '.*')
ON CONFLICT DO NOTHING;
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'ADMIN', '/v1/permissions/module/:module', '.*')
ON CONFLICT DO NOTHING;
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'ADMIN', '/v1/permissions/name/:name', '.*')
ON CONFLICT DO NOTHING;

-- Dashboard access for all admin roles
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'SUPER_ADMIN', '/v1/dashboard', '.*')
ON CONFLICT DO NOTHING;
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'SUPER_ADMIN', '/v1/dashboard/stats', '.*')
ON CONFLICT DO NOTHING;
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'SUPER_ADMIN', '/v1/dashboard/orders', '.*')
ON CONFLICT DO NOTHING;
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'SUPER_ADMIN', '/v1/dashboard/merchants', '.*')
ON CONFLICT DO NOTHING;
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'SUPER_ADMIN', '/v1/dashboard/products', '.*')
ON CONFLICT DO NOTHING;
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'SUPER_ADMIN', '/v1/dashboard/revenue', '.*')
ON CONFLICT DO NOTHING;
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'SUPER_ADMIN', '/v1/dashboard/top-products', '.*')
ON CONFLICT DO NOTHING;
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'SUPER_ADMIN', '/v1/dashboard/top-merchants', '.*')
ON CONFLICT DO NOTHING;

INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'ADMIN', '/v1/dashboard', '.*')
ON CONFLICT DO NOTHING;
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'ADMIN', '/v1/dashboard/stats', '.*')
ON CONFLICT DO NOTHING;
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'ADMIN', '/v1/dashboard/orders', '.*')
ON CONFLICT DO NOTHING;
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'ADMIN', '/v1/dashboard/merchants', '.*')
ON CONFLICT DO NOTHING;
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'ADMIN', '/v1/dashboard/products', '.*')
ON CONFLICT DO NOTHING;
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'ADMIN', '/v1/dashboard/revenue', '.*')
ON CONFLICT DO NOTHING;
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'ADMIN', '/v1/dashboard/top-products', '.*')
ON CONFLICT DO NOTHING;
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'ADMIN', '/v1/dashboard/top-merchants', '.*')
ON CONFLICT DO NOTHING;

-- Distributor management for SUPER_ADMIN and ADMIN
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'SUPER_ADMIN', '/v1/distributors', '.*')
ON CONFLICT DO NOTHING;
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'SUPER_ADMIN', '/v1/distributors/:id', '.*')
ON CONFLICT DO NOTHING;

INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'ADMIN', '/v1/distributors', '.*')
ON CONFLICT DO NOTHING;
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'ADMIN', '/v1/distributors/:id', '.*')
ON CONFLICT DO NOTHING;

-- User management for SUPER_ADMIN and ADMIN
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'SUPER_ADMIN', '/v1/users', '.*')
ON CONFLICT DO NOTHING;
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'SUPER_ADMIN', '/v1/users/:id', '.*')
ON CONFLICT DO NOTHING;
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'SUPER_ADMIN', '/v1/users/roles', '.*')
ON CONFLICT DO NOTHING;
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'SUPER_ADMIN', '/v1/users/distributor/:id', '.*')
ON CONFLICT DO NOTHING;
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'SUPER_ADMIN', '/v1/users/role/:role', '.*')
ON CONFLICT DO NOTHING;
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'SUPER_ADMIN', '/v1/users/:id/reset-password', '.*')
ON CONFLICT DO NOTHING;
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'SUPER_ADMIN', '/v1/users/:id/activate', '.*')
ON CONFLICT DO NOTHING;
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'SUPER_ADMIN', '/v1/users/:id/deactivate', '.*')
ON CONFLICT DO NOTHING;

INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'ADMIN', '/v1/users', '.*')
ON CONFLICT DO NOTHING;
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'ADMIN', '/v1/users/:id', '.*')
ON CONFLICT DO NOTHING;
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'ADMIN', '/v1/users/roles', '.*')
ON CONFLICT DO NOTHING;
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'ADMIN', '/v1/users/distributor/:id', '.*')
ON CONFLICT DO NOTHING;
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'ADMIN', '/v1/users/role/:role', '.*')
ON CONFLICT DO NOTHING;
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'ADMIN', '/v1/users/:id/reset-password', '.*')
ON CONFLICT DO NOTHING;
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'ADMIN', '/v1/users/:id/activate', '.*')
ON CONFLICT DO NOTHING;
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'ADMIN', '/v1/users/:id/deactivate', '.*')
ON CONFLICT DO NOTHING;

-- Dashboard access for DISTRIBUTOR_ADMIN
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'DISTRIBUTOR_ADMIN', '/v1/dashboard', 'GET')
ON CONFLICT DO NOTHING;
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'DISTRIBUTOR_ADMIN', '/v1/dashboard/stats', 'GET')
ON CONFLICT DO NOTHING;
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'DISTRIBUTOR_ADMIN', '/v1/dashboard/orders', 'GET')
ON CONFLICT DO NOTHING;
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'DISTRIBUTOR_ADMIN', '/v1/dashboard/merchants', 'GET')
ON CONFLICT DO NOTHING;
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'DISTRIBUTOR_ADMIN', '/v1/dashboard/products', 'GET')
ON CONFLICT DO NOTHING;
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'DISTRIBUTOR_ADMIN', '/v1/dashboard/revenue', 'GET')
ON CONFLICT DO NOTHING;
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'DISTRIBUTOR_ADMIN', '/v1/dashboard/top-products', 'GET')
ON CONFLICT DO NOTHING;
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'DISTRIBUTOR_ADMIN', '/v1/dashboard/top-merchants', 'GET')
ON CONFLICT DO NOTHING;
