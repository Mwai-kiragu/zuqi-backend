-- Clear existing role_permissions and reassign properly
DELETE FROM role_permissions;

-- ADMIN gets all permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p WHERE r.name = 'ADMIN';

-- DISTRIBUTOR_ADMIN gets most permissions except some admin-only ones
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'DISTRIBUTOR_ADMIN'
AND p.name IN (
    'users:read', 'users:create', 'users:update', 'users:delete',
    'roles:read',
    'merchants:read', 'merchants:create', 'merchants:update', 'merchants:delete', 'merchants:verify',
    'products:read', 'products:create', 'products:update', 'products:delete',
    'orders:read', 'orders:create', 'orders:update', 'orders:delete', 'orders:status',
    'inventory:read', 'inventory:adjust', 'warehouses:read', 'warehouses:manage',
    'payments:read', 'payments:create',
    'credit:read', 'credit:manage',
    'reports:read', 'reports:export',
    'dashboard:view',
    'distributors:read'
);

-- SALES_REP permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'SALES_REP'
AND p.name IN (
    'merchants:read', 'merchants:create', 'merchants:update',
    'products:read',
    'orders:read', 'orders:create', 'orders:update',
    'inventory:read',
    'credit:read',
    'dashboard:view'
);

-- WAREHOUSE_MANAGER permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'WAREHOUSE_MANAGER'
AND p.name IN (
    'products:read',
    'orders:read', 'orders:status',
    'inventory:read', 'inventory:adjust', 'warehouses:read', 'warehouses:manage',
    'dashboard:view'
);

-- MERCHANT permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'MERCHANT'
AND p.name IN (
    'products:read',
    'orders:read', 'orders:create',
    'payments:read', 'payments:create',
    'credit:read',
    'dashboard:view'
);

-- FINANCE permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'FINANCE'
AND p.name IN (
    'orders:read',
    'payments:read', 'payments:create', 'payments:reconcile',
    'credit:read',
    'reports:read', 'reports:export',
    'dashboard:view'
);

-- DRIVER permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'DRIVER'
AND p.name IN (
    'orders:read', 'orders:status',
    'dashboard:view'
);
