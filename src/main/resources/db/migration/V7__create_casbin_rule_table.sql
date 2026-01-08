-- Casbin rule table for database-backed authorization policies
-- This table stores RBAC policies that can be managed dynamically

CREATE TABLE IF NOT EXISTS casbin_rule (
    id SERIAL PRIMARY KEY,
    ptype VARCHAR(100) NOT NULL,
    v0 VARCHAR(100),
    v1 VARCHAR(100),
    v2 VARCHAR(100),
    v3 VARCHAR(100),
    v4 VARCHAR(100),
    v5 VARCHAR(100)
);

-- Index for faster policy lookups
CREATE INDEX idx_casbin_rule_ptype ON casbin_rule(ptype);
CREATE INDEX idx_casbin_rule_v0 ON casbin_rule(v0);
CREATE INDEX idx_casbin_rule_v0_v1 ON casbin_rule(v0, v1);

-- =====================================================
-- POLICY RULES (p = subject, object, action)
-- =====================================================

-- ADMIN - Full access to all resources
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'ADMIN', '/*', '.*');

-- DISTRIBUTOR_ADMIN - Manage their distributor's resources
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'DISTRIBUTOR_ADMIN', '/v1/roles', 'GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'DISTRIBUTOR_ADMIN', '/v1/roles/:id', 'GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'DISTRIBUTOR_ADMIN', '/v1/permissions', 'GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'DISTRIBUTOR_ADMIN', '/v1/permissions/:id', 'GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'DISTRIBUTOR_ADMIN', '/v1/permissions/modules', 'GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'DISTRIBUTOR_ADMIN', '/v1/permissions/module/:module', 'GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'DISTRIBUTOR_ADMIN', '/v1/distributors', 'GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'DISTRIBUTOR_ADMIN', '/v1/distributors/:id', 'GET|PUT|PATCH');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'DISTRIBUTOR_ADMIN', '/v1/distributors/:id/*', '.*');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'DISTRIBUTOR_ADMIN', '/v1/users', 'GET|POST');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'DISTRIBUTOR_ADMIN', '/v1/users/roles', 'GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'DISTRIBUTOR_ADMIN', '/v1/users/distributor/:id', 'GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'DISTRIBUTOR_ADMIN', '/v1/users/role/:role', 'GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'DISTRIBUTOR_ADMIN', '/v1/users/:id', 'GET|PUT|PATCH|DELETE');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'DISTRIBUTOR_ADMIN', '/v1/users/:id/reset-password', 'POST');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'DISTRIBUTOR_ADMIN', '/v1/users/:id/activate', 'POST');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'DISTRIBUTOR_ADMIN', '/v1/merchants', '.*');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'DISTRIBUTOR_ADMIN', '/v1/merchants/:id', '.*');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'DISTRIBUTOR_ADMIN', '/v1/products', '.*');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'DISTRIBUTOR_ADMIN', '/v1/products/:id', '.*');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'DISTRIBUTOR_ADMIN', '/v1/inventory', '.*');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'DISTRIBUTOR_ADMIN', '/v1/inventory/:id', '.*');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'DISTRIBUTOR_ADMIN', '/v1/orders', '.*');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'DISTRIBUTOR_ADMIN', '/v1/orders/:id', '.*');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'DISTRIBUTOR_ADMIN', '/v1/payments', 'GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'DISTRIBUTOR_ADMIN', '/v1/payments/:id', 'GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'DISTRIBUTOR_ADMIN', '/v1/credit', '.*');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'DISTRIBUTOR_ADMIN', '/v1/credit/:id', '.*');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'DISTRIBUTOR_ADMIN', '/v1/reports', 'GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'DISTRIBUTOR_ADMIN', '/v1/reports/:id', 'GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'DISTRIBUTOR_ADMIN', '/v1/dashboard', 'GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'DISTRIBUTOR_ADMIN', '/v1/dashboard/*', 'GET');

-- SALES_REP - Field sales operations
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'SALES_REP', '/v1/merchants', 'GET|POST');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'SALES_REP', '/v1/merchants/:id', 'GET|PUT|PATCH');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'SALES_REP', '/v1/orders', 'GET|POST');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'SALES_REP', '/v1/orders/:id', 'GET|PUT|PATCH');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'SALES_REP', '/v1/products', 'GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'SALES_REP', '/v1/products/:id', 'GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'SALES_REP', '/v1/inventory', 'GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'SALES_REP', '/v1/credit/:id', 'GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'SALES_REP', '/v1/dashboard', 'GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'SALES_REP', '/v1/dashboard/*', 'GET');

-- WAREHOUSE_MANAGER - Inventory and delivery operations
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'WAREHOUSE_MANAGER', '/v1/distributors', 'GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'WAREHOUSE_MANAGER', '/v1/inventory', '.*');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'WAREHOUSE_MANAGER', '/v1/inventory/:id', '.*');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'WAREHOUSE_MANAGER', '/v1/products', 'GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'WAREHOUSE_MANAGER', '/v1/products/:id', 'GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'WAREHOUSE_MANAGER', '/v1/orders', 'GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'WAREHOUSE_MANAGER', '/v1/orders/:id', 'GET|PUT|PATCH');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'WAREHOUSE_MANAGER', '/v1/dashboard', 'GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'WAREHOUSE_MANAGER', '/v1/dashboard/*', 'GET');

-- MERCHANT - Customer/retailer access
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'MERCHANT', '/v1/orders', 'GET|POST');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'MERCHANT', '/v1/orders/:id', 'GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'MERCHANT', '/v1/products', 'GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'MERCHANT', '/v1/products/:id', 'GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'MERCHANT', '/v1/payments', 'GET|POST');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'MERCHANT', '/v1/payments/:id', 'GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'MERCHANT', '/v1/credit', 'GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'MERCHANT', '/v1/dashboard', 'GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'MERCHANT', '/v1/dashboard/*', 'GET');

-- FINANCE - Payment and reconciliation
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'FINANCE', '/v1/payments', '.*');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'FINANCE', '/v1/payments/:id', '.*');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'FINANCE', '/v1/reports', 'GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'FINANCE', '/v1/reports/:id', 'GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'FINANCE', '/v1/credit', 'GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'FINANCE', '/v1/credit/:id', 'GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'FINANCE', '/v1/orders', 'GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'FINANCE', '/v1/orders/:id', 'GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'FINANCE', '/v1/dashboard', 'GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'FINANCE', '/v1/dashboard/*', 'GET');

-- DRIVER - Delivery operations
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'DRIVER', '/v1/orders', 'GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'DRIVER', '/v1/orders/:id', 'GET|PATCH');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'DRIVER', '/v1/dashboard', 'GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'DRIVER', '/v1/dashboard/*', 'GET');

-- =====================================================
-- PROFILE ACCESS - All authenticated users
-- =====================================================
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'DISTRIBUTOR_ADMIN', '/v1/users/me', 'GET|PUT');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'DISTRIBUTOR_ADMIN', '/v1/users/me/change-password', 'POST');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'SALES_REP', '/v1/users/me', 'GET|PUT');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'SALES_REP', '/v1/users/me/change-password', 'POST');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'WAREHOUSE_MANAGER', '/v1/users/me', 'GET|PUT');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'WAREHOUSE_MANAGER', '/v1/users/me/change-password', 'POST');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'MERCHANT', '/v1/users/me', 'GET|PUT');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'MERCHANT', '/v1/users/me/change-password', 'POST');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'FINANCE', '/v1/users/me', 'GET|PUT');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'FINANCE', '/v1/users/me/change-password', 'POST');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'DRIVER', '/v1/users/me', 'GET|PUT');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'DRIVER', '/v1/users/me/change-password', 'POST');

-- =====================================================
-- ROLE HIERARCHY (g = role inheritance)
-- =====================================================
INSERT INTO casbin_rule (ptype, v0, v1) VALUES ('g', 'ADMIN', 'DISTRIBUTOR_ADMIN');
INSERT INTO casbin_rule (ptype, v0, v1) VALUES ('g', 'DISTRIBUTOR_ADMIN', 'FINANCE');
INSERT INTO casbin_rule (ptype, v0, v1) VALUES ('g', 'DISTRIBUTOR_ADMIN', 'WAREHOUSE_MANAGER');
INSERT INTO casbin_rule (ptype, v0, v1) VALUES ('g', 'DISTRIBUTOR_ADMIN', 'SALES_REP');
