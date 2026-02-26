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
CREATE INDEX IF NOT EXISTS idx_casbin_rule_ptype ON casbin_rule(ptype);
CREATE INDEX IF NOT EXISTS idx_casbin_rule_v0 ON casbin_rule(v0);
CREATE INDEX IF NOT EXISTS idx_casbin_rule_v0_v1 ON casbin_rule(v0, v1);

-- =====================================================
-- POLICY RULES (p = subject, object, action)
-- =====================================================

-- ADMIN - Full access to all resources
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'ADMIN', '/*', '.*' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='ADMIN' AND v1='/*' AND v2='.*');

-- DISTRIBUTOR_ADMIN - Manage their distributor's resources
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'DISTRIBUTOR_ADMIN', '/v1/roles', 'GET' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='DISTRIBUTOR_ADMIN' AND v1='/v1/roles' AND v2='GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'DISTRIBUTOR_ADMIN', '/v1/roles/:id', 'GET' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='DISTRIBUTOR_ADMIN' AND v1='/v1/roles/:id' AND v2='GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'DISTRIBUTOR_ADMIN', '/v1/permissions', 'GET' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='DISTRIBUTOR_ADMIN' AND v1='/v1/permissions' AND v2='GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'DISTRIBUTOR_ADMIN', '/v1/permissions/:id', 'GET' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='DISTRIBUTOR_ADMIN' AND v1='/v1/permissions/:id' AND v2='GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'DISTRIBUTOR_ADMIN', '/v1/permissions/modules', 'GET' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='DISTRIBUTOR_ADMIN' AND v1='/v1/permissions/modules' AND v2='GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'DISTRIBUTOR_ADMIN', '/v1/permissions/module/:module', 'GET' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='DISTRIBUTOR_ADMIN' AND v1='/v1/permissions/module/:module' AND v2='GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'DISTRIBUTOR_ADMIN', '/v1/distributors', 'GET' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='DISTRIBUTOR_ADMIN' AND v1='/v1/distributors' AND v2='GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'DISTRIBUTOR_ADMIN', '/v1/distributors/:id', 'GET|PUT|PATCH' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='DISTRIBUTOR_ADMIN' AND v1='/v1/distributors/:id' AND v2='GET|PUT|PATCH');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'DISTRIBUTOR_ADMIN', '/v1/distributors/:id/*', '.*' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='DISTRIBUTOR_ADMIN' AND v1='/v1/distributors/:id/*' AND v2='.*');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'DISTRIBUTOR_ADMIN', '/v1/users', 'GET|POST' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='DISTRIBUTOR_ADMIN' AND v1='/v1/users' AND v2='GET|POST');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'DISTRIBUTOR_ADMIN', '/v1/users/roles', 'GET' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='DISTRIBUTOR_ADMIN' AND v1='/v1/users/roles' AND v2='GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'DISTRIBUTOR_ADMIN', '/v1/users/distributor/:id', 'GET' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='DISTRIBUTOR_ADMIN' AND v1='/v1/users/distributor/:id' AND v2='GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'DISTRIBUTOR_ADMIN', '/v1/users/role/:role', 'GET' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='DISTRIBUTOR_ADMIN' AND v1='/v1/users/role/:role' AND v2='GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'DISTRIBUTOR_ADMIN', '/v1/users/:id', 'GET|PUT|PATCH|DELETE' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='DISTRIBUTOR_ADMIN' AND v1='/v1/users/:id' AND v2='GET|PUT|PATCH|DELETE');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'DISTRIBUTOR_ADMIN', '/v1/users/:id/reset-password', 'POST' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='DISTRIBUTOR_ADMIN' AND v1='/v1/users/:id/reset-password' AND v2='POST');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'DISTRIBUTOR_ADMIN', '/v1/users/:id/activate', 'POST' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='DISTRIBUTOR_ADMIN' AND v1='/v1/users/:id/activate' AND v2='POST');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'DISTRIBUTOR_ADMIN', '/v1/merchants', '.*' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='DISTRIBUTOR_ADMIN' AND v1='/v1/merchants' AND v2='.*');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'DISTRIBUTOR_ADMIN', '/v1/merchants/:id', '.*' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='DISTRIBUTOR_ADMIN' AND v1='/v1/merchants/:id' AND v2='.*');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'DISTRIBUTOR_ADMIN', '/v1/products', '.*' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='DISTRIBUTOR_ADMIN' AND v1='/v1/products' AND v2='.*');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'DISTRIBUTOR_ADMIN', '/v1/products/:id', '.*' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='DISTRIBUTOR_ADMIN' AND v1='/v1/products/:id' AND v2='.*');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'DISTRIBUTOR_ADMIN', '/v1/inventory', '.*' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='DISTRIBUTOR_ADMIN' AND v1='/v1/inventory' AND v2='.*');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'DISTRIBUTOR_ADMIN', '/v1/inventory/:id', '.*' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='DISTRIBUTOR_ADMIN' AND v1='/v1/inventory/:id' AND v2='.*');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'DISTRIBUTOR_ADMIN', '/v1/orders', '.*' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='DISTRIBUTOR_ADMIN' AND v1='/v1/orders' AND v2='.*');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'DISTRIBUTOR_ADMIN', '/v1/orders/:id', '.*' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='DISTRIBUTOR_ADMIN' AND v1='/v1/orders/:id' AND v2='.*');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'DISTRIBUTOR_ADMIN', '/v1/payments', 'GET' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='DISTRIBUTOR_ADMIN' AND v1='/v1/payments' AND v2='GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'DISTRIBUTOR_ADMIN', '/v1/payments/:id', 'GET' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='DISTRIBUTOR_ADMIN' AND v1='/v1/payments/:id' AND v2='GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'DISTRIBUTOR_ADMIN', '/v1/credit', '.*' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='DISTRIBUTOR_ADMIN' AND v1='/v1/credit' AND v2='.*');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'DISTRIBUTOR_ADMIN', '/v1/credit/:id', '.*' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='DISTRIBUTOR_ADMIN' AND v1='/v1/credit/:id' AND v2='.*');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'DISTRIBUTOR_ADMIN', '/v1/reports', 'GET' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='DISTRIBUTOR_ADMIN' AND v1='/v1/reports' AND v2='GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'DISTRIBUTOR_ADMIN', '/v1/reports/:id', 'GET' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='DISTRIBUTOR_ADMIN' AND v1='/v1/reports/:id' AND v2='GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'DISTRIBUTOR_ADMIN', '/v1/dashboard', 'GET' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='DISTRIBUTOR_ADMIN' AND v1='/v1/dashboard' AND v2='GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'DISTRIBUTOR_ADMIN', '/v1/dashboard/*', 'GET' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='DISTRIBUTOR_ADMIN' AND v1='/v1/dashboard/*' AND v2='GET');

-- SALES_REP - Field sales operations
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'SALES_REP', '/v1/merchants', 'GET|POST' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='SALES_REP' AND v1='/v1/merchants' AND v2='GET|POST');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'SALES_REP', '/v1/merchants/:id', 'GET|PUT|PATCH' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='SALES_REP' AND v1='/v1/merchants/:id' AND v2='GET|PUT|PATCH');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'SALES_REP', '/v1/orders', 'GET|POST' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='SALES_REP' AND v1='/v1/orders' AND v2='GET|POST');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'SALES_REP', '/v1/orders/:id', 'GET|PUT|PATCH' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='SALES_REP' AND v1='/v1/orders/:id' AND v2='GET|PUT|PATCH');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'SALES_REP', '/v1/products', 'GET' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='SALES_REP' AND v1='/v1/products' AND v2='GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'SALES_REP', '/v1/products/:id', 'GET' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='SALES_REP' AND v1='/v1/products/:id' AND v2='GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'SALES_REP', '/v1/inventory', 'GET' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='SALES_REP' AND v1='/v1/inventory' AND v2='GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'SALES_REP', '/v1/credit/:id', 'GET' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='SALES_REP' AND v1='/v1/credit/:id' AND v2='GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'SALES_REP', '/v1/dashboard', 'GET' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='SALES_REP' AND v1='/v1/dashboard' AND v2='GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'SALES_REP', '/v1/dashboard/*', 'GET' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='SALES_REP' AND v1='/v1/dashboard/*' AND v2='GET');

-- WAREHOUSE_MANAGER - Inventory and delivery operations
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'WAREHOUSE_MANAGER', '/v1/distributors', 'GET' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='WAREHOUSE_MANAGER' AND v1='/v1/distributors' AND v2='GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'WAREHOUSE_MANAGER', '/v1/inventory', '.*' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='WAREHOUSE_MANAGER' AND v1='/v1/inventory' AND v2='.*');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'WAREHOUSE_MANAGER', '/v1/inventory/:id', '.*' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='WAREHOUSE_MANAGER' AND v1='/v1/inventory/:id' AND v2='.*');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'WAREHOUSE_MANAGER', '/v1/products', 'GET' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='WAREHOUSE_MANAGER' AND v1='/v1/products' AND v2='GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'WAREHOUSE_MANAGER', '/v1/products/:id', 'GET' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='WAREHOUSE_MANAGER' AND v1='/v1/products/:id' AND v2='GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'WAREHOUSE_MANAGER', '/v1/orders', 'GET' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='WAREHOUSE_MANAGER' AND v1='/v1/orders' AND v2='GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'WAREHOUSE_MANAGER', '/v1/orders/:id', 'GET|PUT|PATCH' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='WAREHOUSE_MANAGER' AND v1='/v1/orders/:id' AND v2='GET|PUT|PATCH');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'WAREHOUSE_MANAGER', '/v1/dashboard', 'GET' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='WAREHOUSE_MANAGER' AND v1='/v1/dashboard' AND v2='GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'WAREHOUSE_MANAGER', '/v1/dashboard/*', 'GET' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='WAREHOUSE_MANAGER' AND v1='/v1/dashboard/*' AND v2='GET');

-- MERCHANT - Customer/retailer access
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'MERCHANT', '/v1/orders', 'GET|POST' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='MERCHANT' AND v1='/v1/orders' AND v2='GET|POST');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'MERCHANT', '/v1/orders/:id', 'GET' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='MERCHANT' AND v1='/v1/orders/:id' AND v2='GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'MERCHANT', '/v1/products', 'GET' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='MERCHANT' AND v1='/v1/products' AND v2='GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'MERCHANT', '/v1/products/:id', 'GET' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='MERCHANT' AND v1='/v1/products/:id' AND v2='GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'MERCHANT', '/v1/payments', 'GET|POST' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='MERCHANT' AND v1='/v1/payments' AND v2='GET|POST');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'MERCHANT', '/v1/payments/:id', 'GET' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='MERCHANT' AND v1='/v1/payments/:id' AND v2='GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'MERCHANT', '/v1/credit', 'GET' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='MERCHANT' AND v1='/v1/credit' AND v2='GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'MERCHANT', '/v1/dashboard', 'GET' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='MERCHANT' AND v1='/v1/dashboard' AND v2='GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'MERCHANT', '/v1/dashboard/*', 'GET' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='MERCHANT' AND v1='/v1/dashboard/*' AND v2='GET');

-- FINANCE - Payment and reconciliation
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'FINANCE', '/v1/payments', '.*' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='FINANCE' AND v1='/v1/payments' AND v2='.*');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'FINANCE', '/v1/payments/:id', '.*' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='FINANCE' AND v1='/v1/payments/:id' AND v2='.*');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'FINANCE', '/v1/reports', 'GET' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='FINANCE' AND v1='/v1/reports' AND v2='GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'FINANCE', '/v1/reports/:id', 'GET' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='FINANCE' AND v1='/v1/reports/:id' AND v2='GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'FINANCE', '/v1/credit', 'GET' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='FINANCE' AND v1='/v1/credit' AND v2='GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'FINANCE', '/v1/credit/:id', 'GET' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='FINANCE' AND v1='/v1/credit/:id' AND v2='GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'FINANCE', '/v1/orders', 'GET' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='FINANCE' AND v1='/v1/orders' AND v2='GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'FINANCE', '/v1/orders/:id', 'GET' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='FINANCE' AND v1='/v1/orders/:id' AND v2='GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'FINANCE', '/v1/dashboard', 'GET' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='FINANCE' AND v1='/v1/dashboard' AND v2='GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'FINANCE', '/v1/dashboard/*', 'GET' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='FINANCE' AND v1='/v1/dashboard/*' AND v2='GET');

-- DRIVER - Delivery operations
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'DRIVER', '/v1/orders', 'GET' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='DRIVER' AND v1='/v1/orders' AND v2='GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'DRIVER', '/v1/orders/:id', 'GET|PATCH' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='DRIVER' AND v1='/v1/orders/:id' AND v2='GET|PATCH');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'DRIVER', '/v1/dashboard', 'GET' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='DRIVER' AND v1='/v1/dashboard' AND v2='GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'DRIVER', '/v1/dashboard/*', 'GET' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='DRIVER' AND v1='/v1/dashboard/*' AND v2='GET');

-- =====================================================
-- PROFILE ACCESS - All authenticated users
-- =====================================================
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'DISTRIBUTOR_ADMIN', '/v1/users/me', 'GET|PUT' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='DISTRIBUTOR_ADMIN' AND v1='/v1/users/me' AND v2='GET|PUT');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'DISTRIBUTOR_ADMIN', '/v1/users/me/change-password', 'POST' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='DISTRIBUTOR_ADMIN' AND v1='/v1/users/me/change-password' AND v2='POST');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'SALES_REP', '/v1/users/me', 'GET|PUT' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='SALES_REP' AND v1='/v1/users/me' AND v2='GET|PUT');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'SALES_REP', '/v1/users/me/change-password', 'POST' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='SALES_REP' AND v1='/v1/users/me/change-password' AND v2='POST');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'WAREHOUSE_MANAGER', '/v1/users/me', 'GET|PUT' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='WAREHOUSE_MANAGER' AND v1='/v1/users/me' AND v2='GET|PUT');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'WAREHOUSE_MANAGER', '/v1/users/me/change-password', 'POST' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='WAREHOUSE_MANAGER' AND v1='/v1/users/me/change-password' AND v2='POST');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'MERCHANT', '/v1/users/me', 'GET|PUT' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='MERCHANT' AND v1='/v1/users/me' AND v2='GET|PUT');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'MERCHANT', '/v1/users/me/change-password', 'POST' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='MERCHANT' AND v1='/v1/users/me/change-password' AND v2='POST');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'FINANCE', '/v1/users/me', 'GET|PUT' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='FINANCE' AND v1='/v1/users/me' AND v2='GET|PUT');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'FINANCE', '/v1/users/me/change-password', 'POST' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='FINANCE' AND v1='/v1/users/me/change-password' AND v2='POST');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'DRIVER', '/v1/users/me', 'GET|PUT' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='DRIVER' AND v1='/v1/users/me' AND v2='GET|PUT');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'DRIVER', '/v1/users/me/change-password', 'POST' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='DRIVER' AND v1='/v1/users/me/change-password' AND v2='POST');

-- =====================================================
-- ROLE HIERARCHY (g = role inheritance)
-- =====================================================
INSERT INTO casbin_rule (ptype, v0, v1) SELECT 'g', 'ADMIN', 'DISTRIBUTOR_ADMIN' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='g' AND v0='ADMIN' AND v1='DISTRIBUTOR_ADMIN');
INSERT INTO casbin_rule (ptype, v0, v1) SELECT 'g', 'DISTRIBUTOR_ADMIN', 'FINANCE' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='g' AND v0='DISTRIBUTOR_ADMIN' AND v1='FINANCE');
INSERT INTO casbin_rule (ptype, v0, v1) SELECT 'g', 'DISTRIBUTOR_ADMIN', 'WAREHOUSE_MANAGER' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='g' AND v0='DISTRIBUTOR_ADMIN' AND v1='WAREHOUSE_MANAGER');
INSERT INTO casbin_rule (ptype, v0, v1) SELECT 'g', 'DISTRIBUTOR_ADMIN', 'SALES_REP' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='g' AND v0='DISTRIBUTOR_ADMIN' AND v1='SALES_REP');
