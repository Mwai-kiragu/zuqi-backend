-- Add Casbin policies for product category endpoints

-- SUPER_ADMIN - full access to categories
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'SUPER_ADMIN', '/v1/products/categories', '.*' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='SUPER_ADMIN' AND v1='/v1/products/categories' AND v2='.*');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'SUPER_ADMIN', '/v1/products/categories/:id', '.*' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='SUPER_ADMIN' AND v1='/v1/products/categories/:id' AND v2='.*');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'SUPER_ADMIN', '/v1/products/categories/:id/activate', 'POST' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='SUPER_ADMIN' AND v1='/v1/products/categories/:id/activate' AND v2='POST');

-- DISTRIBUTOR_ADMIN - full access to categories
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'DISTRIBUTOR_ADMIN', '/v1/products/categories', '.*' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='DISTRIBUTOR_ADMIN' AND v1='/v1/products/categories' AND v2='.*');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'DISTRIBUTOR_ADMIN', '/v1/products/categories/:id', '.*' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='DISTRIBUTOR_ADMIN' AND v1='/v1/products/categories/:id' AND v2='.*');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'DISTRIBUTOR_ADMIN', '/v1/products/categories/:id/activate', 'POST' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='DISTRIBUTOR_ADMIN' AND v1='/v1/products/categories/:id/activate' AND v2='POST');

-- SALES_REP - read-only access to categories
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'SALES_REP', '/v1/products/categories', 'GET' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='SALES_REP' AND v1='/v1/products/categories' AND v2='GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'SALES_REP', '/v1/products/categories/:id', 'GET' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='SALES_REP' AND v1='/v1/products/categories/:id' AND v2='GET');

-- WAREHOUSE_MANAGER - read-only access to categories
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'WAREHOUSE_MANAGER', '/v1/products/categories', 'GET' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='WAREHOUSE_MANAGER' AND v1='/v1/products/categories' AND v2='GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'WAREHOUSE_MANAGER', '/v1/products/categories/:id', 'GET' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='WAREHOUSE_MANAGER' AND v1='/v1/products/categories/:id' AND v2='GET');

-- MERCHANT - read-only access to categories
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'MERCHANT', '/v1/products/categories', 'GET' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='MERCHANT' AND v1='/v1/products/categories' AND v2='GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'MERCHANT', '/v1/products/categories/:id', 'GET' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='MERCHANT' AND v1='/v1/products/categories/:id' AND v2='GET');
