-- Add Casbin policies for product category endpoints

-- SUPER_ADMIN - full access to categories
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'SUPER_ADMIN', '/v1/products/categories', '.*');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'SUPER_ADMIN', '/v1/products/categories/:id', '.*');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'SUPER_ADMIN', '/v1/products/categories/:id/activate', 'POST');

-- DISTRIBUTOR_ADMIN - full access to categories
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'DISTRIBUTOR_ADMIN', '/v1/products/categories', '.*');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'DISTRIBUTOR_ADMIN', '/v1/products/categories/:id', '.*');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'DISTRIBUTOR_ADMIN', '/v1/products/categories/:id/activate', 'POST');

-- SALES_REP - read-only access to categories
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'SALES_REP', '/v1/products/categories', 'GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'SALES_REP', '/v1/products/categories/:id', 'GET');

-- WAREHOUSE_MANAGER - read-only access to categories
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'WAREHOUSE_MANAGER', '/v1/products/categories', 'GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'WAREHOUSE_MANAGER', '/v1/products/categories/:id', 'GET');

-- MERCHANT - read-only access to categories
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'MERCHANT', '/v1/products/categories', 'GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'MERCHANT', '/v1/products/categories/:id', 'GET');
