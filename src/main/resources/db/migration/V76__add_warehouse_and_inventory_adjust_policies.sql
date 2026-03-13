-- Add explicit warehouse management and inventory adjustment policies
-- Fixes: MERCHANT_ADMIN, DISTRIBUTOR_ADMIN, WAREHOUSE_MANAGER access to /v1/inventory sub-paths

-- ============================================================
-- MERCHANT_ADMIN: full warehouse CRUD + stock adjust
-- ============================================================
INSERT INTO casbin_rule (ptype, v0, v1, v2)
SELECT 'p', 'MERCHANT_ADMIN', '/v1/inventory/warehouses', 'GET|POST'
WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='MERCHANT_ADMIN' AND v1='/v1/inventory/warehouses' AND v2='GET|POST');

INSERT INTO casbin_rule (ptype, v0, v1, v2)
SELECT 'p', 'MERCHANT_ADMIN', '/v1/inventory/warehouses/page', 'GET'
WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='MERCHANT_ADMIN' AND v1='/v1/inventory/warehouses/page' AND v2='GET');

INSERT INTO casbin_rule (ptype, v0, v1, v2)
SELECT 'p', 'MERCHANT_ADMIN', '/v1/inventory/warehouses/:id', 'GET|PUT|DELETE'
WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='MERCHANT_ADMIN' AND v1='/v1/inventory/warehouses/:id' AND v2='GET|PUT|DELETE');

INSERT INTO casbin_rule (ptype, v0, v1, v2)
SELECT 'p', 'MERCHANT_ADMIN', '/v1/inventory/warehouses/:id/activate', 'POST'
WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='MERCHANT_ADMIN' AND v1='/v1/inventory/warehouses/:id/activate' AND v2='POST');

INSERT INTO casbin_rule (ptype, v0, v1, v2)
SELECT 'p', 'MERCHANT_ADMIN', '/v1/inventory/adjust', 'POST'
WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='MERCHANT_ADMIN' AND v1='/v1/inventory/adjust' AND v2='POST');

INSERT INTO casbin_rule (ptype, v0, v1, v2)
SELECT 'p', 'MERCHANT_ADMIN', '/v1/inventory/low-stock', 'GET'
WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='MERCHANT_ADMIN' AND v1='/v1/inventory/low-stock' AND v2='GET');

INSERT INTO casbin_rule (ptype, v0, v1, v2)
SELECT 'p', 'MERCHANT_ADMIN', '/v1/inventory/warehouse/:id', 'GET'
WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='MERCHANT_ADMIN' AND v1='/v1/inventory/warehouse/:id' AND v2='GET');

INSERT INTO casbin_rule (ptype, v0, v1, v2)
SELECT 'p', 'MERCHANT_ADMIN', '/v1/inventory/warehouse/:id/product/:id', 'GET'
WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='MERCHANT_ADMIN' AND v1='/v1/inventory/warehouse/:id/product/:id' AND v2='GET');

INSERT INTO casbin_rule (ptype, v0, v1, v2)
SELECT 'p', 'MERCHANT_ADMIN', '/v1/inventory/product/:id', 'GET'
WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='MERCHANT_ADMIN' AND v1='/v1/inventory/product/:id' AND v2='GET');

INSERT INTO casbin_rule (ptype, v0, v1, v2)
SELECT 'p', 'MERCHANT_ADMIN', '/v1/inventory/movements/warehouse/:id', 'GET'
WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='MERCHANT_ADMIN' AND v1='/v1/inventory/movements/warehouse/:id' AND v2='GET');

-- ============================================================
-- DISTRIBUTOR_ADMIN: explicit warehouse sub-paths (belt-and-suspenders)
-- ============================================================
INSERT INTO casbin_rule (ptype, v0, v1, v2)
SELECT 'p', 'DISTRIBUTOR_ADMIN', '/v1/inventory/warehouses', '.*'
WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='DISTRIBUTOR_ADMIN' AND v1='/v1/inventory/warehouses' AND v2='.*');

INSERT INTO casbin_rule (ptype, v0, v1, v2)
SELECT 'p', 'DISTRIBUTOR_ADMIN', '/v1/inventory/warehouses/page', 'GET'
WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='DISTRIBUTOR_ADMIN' AND v1='/v1/inventory/warehouses/page' AND v2='GET');

INSERT INTO casbin_rule (ptype, v0, v1, v2)
SELECT 'p', 'DISTRIBUTOR_ADMIN', '/v1/inventory/warehouses/:id', '.*'
WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='DISTRIBUTOR_ADMIN' AND v1='/v1/inventory/warehouses/:id' AND v2='.*');

INSERT INTO casbin_rule (ptype, v0, v1, v2)
SELECT 'p', 'DISTRIBUTOR_ADMIN', '/v1/inventory/warehouses/:id/activate', 'POST'
WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='DISTRIBUTOR_ADMIN' AND v1='/v1/inventory/warehouses/:id/activate' AND v2='POST');

INSERT INTO casbin_rule (ptype, v0, v1, v2)
SELECT 'p', 'DISTRIBUTOR_ADMIN', '/v1/inventory/adjust', '.*'
WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='DISTRIBUTOR_ADMIN' AND v1='/v1/inventory/adjust' AND v2='.*');

INSERT INTO casbin_rule (ptype, v0, v1, v2)
SELECT 'p', 'DISTRIBUTOR_ADMIN', '/v1/inventory/low-stock', 'GET'
WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='DISTRIBUTOR_ADMIN' AND v1='/v1/inventory/low-stock' AND v2='GET');

INSERT INTO casbin_rule (ptype, v0, v1, v2)
SELECT 'p', 'DISTRIBUTOR_ADMIN', '/v1/inventory/warehouse/:id', 'GET'
WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='DISTRIBUTOR_ADMIN' AND v1='/v1/inventory/warehouse/:id' AND v2='GET');

INSERT INTO casbin_rule (ptype, v0, v1, v2)
SELECT 'p', 'DISTRIBUTOR_ADMIN', '/v1/inventory/warehouse/:id/product/:id', 'GET'
WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='DISTRIBUTOR_ADMIN' AND v1='/v1/inventory/warehouse/:id/product/:id' AND v2='GET');

INSERT INTO casbin_rule (ptype, v0, v1, v2)
SELECT 'p', 'DISTRIBUTOR_ADMIN', '/v1/inventory/product/:id', 'GET'
WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='DISTRIBUTOR_ADMIN' AND v1='/v1/inventory/product/:id' AND v2='GET');

INSERT INTO casbin_rule (ptype, v0, v1, v2)
SELECT 'p', 'DISTRIBUTOR_ADMIN', '/v1/inventory/movements/warehouse/:id', 'GET'
WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='DISTRIBUTOR_ADMIN' AND v1='/v1/inventory/movements/warehouse/:id' AND v2='GET');

-- ============================================================
-- WAREHOUSE_MANAGER: explicit warehouse sub-paths
-- ============================================================
INSERT INTO casbin_rule (ptype, v0, v1, v2)
SELECT 'p', 'WAREHOUSE_MANAGER', '/v1/inventory/warehouses', 'GET|POST'
WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='WAREHOUSE_MANAGER' AND v1='/v1/inventory/warehouses' AND v2='GET|POST');

INSERT INTO casbin_rule (ptype, v0, v1, v2)
SELECT 'p', 'WAREHOUSE_MANAGER', '/v1/inventory/warehouses/page', 'GET'
WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='WAREHOUSE_MANAGER' AND v1='/v1/inventory/warehouses/page' AND v2='GET');

INSERT INTO casbin_rule (ptype, v0, v1, v2)
SELECT 'p', 'WAREHOUSE_MANAGER', '/v1/inventory/warehouses/:id', '.*'
WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='WAREHOUSE_MANAGER' AND v1='/v1/inventory/warehouses/:id' AND v2='.*');

INSERT INTO casbin_rule (ptype, v0, v1, v2)
SELECT 'p', 'WAREHOUSE_MANAGER', '/v1/inventory/warehouses/:id/activate', 'POST'
WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='WAREHOUSE_MANAGER' AND v1='/v1/inventory/warehouses/:id/activate' AND v2='POST');

INSERT INTO casbin_rule (ptype, v0, v1, v2)
SELECT 'p', 'WAREHOUSE_MANAGER', '/v1/inventory/adjust', '.*'
WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='WAREHOUSE_MANAGER' AND v1='/v1/inventory/adjust' AND v2='.*');

INSERT INTO casbin_rule (ptype, v0, v1, v2)
SELECT 'p', 'WAREHOUSE_MANAGER', '/v1/inventory/low-stock', 'GET'
WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='WAREHOUSE_MANAGER' AND v1='/v1/inventory/low-stock' AND v2='GET');

INSERT INTO casbin_rule (ptype, v0, v1, v2)
SELECT 'p', 'WAREHOUSE_MANAGER', '/v1/inventory/warehouse/:id', 'GET'
WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='WAREHOUSE_MANAGER' AND v1='/v1/inventory/warehouse/:id' AND v2='GET');

INSERT INTO casbin_rule (ptype, v0, v1, v2)
SELECT 'p', 'WAREHOUSE_MANAGER', '/v1/inventory/product/:id', 'GET'
WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='WAREHOUSE_MANAGER' AND v1='/v1/inventory/product/:id' AND v2='GET');

INSERT INTO casbin_rule (ptype, v0, v1, v2)
SELECT 'p', 'WAREHOUSE_MANAGER', '/v1/inventory/movements/warehouse/:id', 'GET'
WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='WAREHOUSE_MANAGER' AND v1='/v1/inventory/movements/warehouse/:id' AND v2='GET');
