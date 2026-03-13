-- Branch permissions
INSERT INTO permissions (name, description, module) VALUES
    ('branches:read',     'View branches',          'BRANCHES'),
    ('branches:create',   'Create branches',         'BRANCHES'),
    ('branches:update',   'Update branches',         'BRANCHES'),
    ('branches:delete',   'Deactivate branches',     'BRANCHES'),
    ('branches:users',    'Manage branch users',     'BRANCHES')
ON CONFLICT (name) DO NOTHING;

-- POS permissions
INSERT INTO permissions (name, description, module) VALUES
    ('pos:read',          'View POS terminals & shifts',  'POS'),
    ('pos:manage',        'Manage POS terminals',          'POS'),
    ('pos:sales',         'Create & manage POS sales',     'POS'),
    ('pos:shifts',        'Open & close POS shifts',       'POS')
ON CONFLICT (name) DO NOTHING;

-- Stock Transfers permissions
INSERT INTO permissions (name, description, module) VALUES
    ('stock_transfers:read',     'View stock transfers',     'STOCK_TRANSFERS'),
    ('stock_transfers:create',   'Create stock transfers',   'STOCK_TRANSFERS'),
    ('stock_transfers:approve',  'Approve stock transfers',  'STOCK_TRANSFERS'),
    ('stock_transfers:cancel',   'Cancel stock transfers',   'STOCK_TRANSFERS')
ON CONFLICT (name) DO NOTHING;

-- Stock Takes permissions
INSERT INTO permissions (name, description, module) VALUES
    ('stock_takes:read',     'View stock takes',       'STOCK_TAKES'),
    ('stock_takes:create',   'Create stock takes',     'STOCK_TAKES'),
    ('stock_takes:update',   'Update stock take items','STOCK_TAKES'),
    ('stock_takes:approve',  'Approve stock takes',    'STOCK_TAKES'),
    ('stock_takes:cancel',   'Cancel stock takes',     'STOCK_TAKES')
ON CONFLICT (name) DO NOTHING;

-- Assign branch permissions to roles that already have the BRANCHES module
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE p.module = 'BRANCHES'
  AND r.name IN ('SUPER_ADMIN', 'MERCHANT_ADMIN', 'DISTRIBUTOR_ADMIN')
ON CONFLICT DO NOTHING;

-- Assign POS permissions to roles
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE p.module = 'POS'
  AND r.name IN ('SUPER_ADMIN', 'MERCHANT_ADMIN', 'DISTRIBUTOR_ADMIN')
ON CONFLICT DO NOTHING;

-- Assign Stock Transfers permissions to roles
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE p.module = 'STOCK_TRANSFERS'
  AND r.name IN ('SUPER_ADMIN', 'MERCHANT_ADMIN', 'DISTRIBUTOR_ADMIN', 'WAREHOUSE_MANAGER')
ON CONFLICT DO NOTHING;

-- Assign Stock Takes permissions to roles
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE p.module = 'STOCK_TAKES'
  AND r.name IN ('SUPER_ADMIN', 'MERCHANT_ADMIN', 'DISTRIBUTOR_ADMIN', 'WAREHOUSE_MANAGER')
ON CONFLICT DO NOTHING;
